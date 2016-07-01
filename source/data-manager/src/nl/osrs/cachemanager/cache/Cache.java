package nl.osrs.cachemanager.cache;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

import nl.osrs.cachemanager.cache.data.DataBlock;
import nl.osrs.cachemanager.cache.data.DataPacker;
import nl.osrs.cachemanager.cache.data.Index;
import nl.osrs.cachemanager.cache.data.IndexType;
import nl.osrs.cachemanager.stream.Stream;
import nl.osrs.cachemanager.stream.StreamLoader;
import nl.osrs.item.Item;
import nl.osrs.item.ItemLoader;

public class Cache {
	public static final String CACHE_PATH = "../../data/client/";

	private final DataPacker[] dataPackers;
	
	private RandomAccessFile data;
	private RandomAccessFile[] indices;
	
	public Cache() throws FileNotFoundException {
		int indexFileCount = 5;
		
		data = loadFile("main_file_cache.dat");
		indices = new RandomAccessFile[indexFileCount];
		dataPackers = new DataPacker[indexFileCount];
		
		for (int i = 0; i < indexFileCount; i++) {
			indices[i] = loadFile("main_file_cache.idx" + i);
			dataPackers[i] = new DataPacker(data, indices[i], IndexType.getIndexType(i));
		}
	}
	
	public void loadArchives() throws IOException {
		loadItemArchive();
	}
	
	public ItemDef[] loadItemArchive() throws IOException {
		ItemDef[] items = new ItemDef[12000];
		
		StreamLoader streamLoader = getStreamLoader(2, IndexType.ARCHIVES);
		
		Stream dataStream = new Stream(streamLoader.getDataForName("obj.dat"));
		Stream indexStream = new Stream(streamLoader.getDataForName("obj.idx"));
		
		int totalItems = indexStream.readUnsignedWord() + 21;
		
		int[] streamIndices = new int[totalItems + 50000];
		int i = 2;
		for (int j = 0; j < totalItems - 21; j++) {
			streamIndices[j] = i;
			i += indexStream.readUnsignedWord();
		}
		
		for (Item item : ItemLoader.getItems()) {
			dataStream.currentOffset = streamIndices[item.getId()];
			ItemDef itemDef = new ItemDef(item.getId());
			itemDef.readValues(dataStream);
			
			if (itemDef.id != item.getId())
				System.out.println(item.getName() + " doesn't match: " + item.getId() + " != " + itemDef.id);
			
			items[item.getId()] = itemDef;
		}
		
		return items;
	}
	
	private StreamLoader getStreamLoader(int i, IndexType indexType) throws IOException {
		Index index = dataPackers[indexType.getIndex()].getIndex(i);
		byte[] data = dataPackers[indexType.getIndex()].decompress(index.getOffset());
		
		return new StreamLoader(data);
	}
	
	public byte[] getData(Index index) throws IOException {
		DataPacker packer = dataPackers[index.getType().getIndex()];
		byte[] data = {};
		
		int nextDataBlockId = index.getInitialDataBlockId();
		
		while (nextDataBlockId != 0) {
			DataBlock dataBlock = packer.getDataBlock(nextDataBlockId);
			
			byte[] currentData = data;
			byte[] newData = dataBlock.getData();
			
			data = new byte[currentData.length + newData.length];
			
			for (int i = 0; i < currentData.length; i++)
				data[i] = currentData[i];
			
			for (int i = 0; i < newData.length; i++)
				data[currentData.length + i] = newData[i];
			
			nextDataBlockId = dataBlock.getNextDataBlockId();
		}
		
		return data;
	}
	
	public static RandomAccessFile loadFile(String fileName) throws FileNotFoundException {
		return new RandomAccessFile(new File(CACHE_PATH + fileName), "rw");
	}
}