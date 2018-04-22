package com.ringoram;

public class Configs {
	// thread fixed number
	public static int THREAD_FIXED = 4;

	// server host name and port
	public static String SERVER_HOSTNAME = "localhost";
	public static int SERVER_PORT = 12339;
	
	//block data length
	public static int BLOCK_DATA_LEN = 8;
	//the max real block count in the bucket
	public static int REAL_BLOCK_COUNT = 4;
	//the min dummy block count in the bucket
	public static int DUMMY_BLOCK_COUNT = 6;
	//total bucket count in the tree, must be full binary tree
	public static int BUCKET_COUNT = 7;
	
	//total block count in bucket
	public static int Z = REAL_BLOCK_COUNT + DUMMY_BLOCK_COUNT;
	//total block count in the tree
	public static int BLOCK_COUNT = BUCKET_COUNT * REAL_BLOCK_COUNT;
	//tree height
	public static int HEIGHT = (int) (Math.log(BUCKET_COUNT)/Math.log(2) + 1);
	//total leaf count in the tree
	public static int LEAF_COUNT = (BUCKET_COUNT+1)/2;
	//leaf start index in tree node(root is 0)
	public static int LEAF_START = BUCKET_COUNT - LEAF_COUNT;
	
	//read_counter, meta_buf, valid_bits
	public static int METADATA_BYTES_LEN = 4+4*(Configs.REAL_BLOCK_COUNT + Configs.Z)+Configs.Z;
	
	//shuffle rate
	public static int SHUFFLE_RATE = 4;
	
	//request operation: read or write
	public enum OPERATION{ORAM_ACCESS_READ,ORAM_ACCESS_WRITE};
	
	//bucket store source
	public static String STORAGE_PATH = "/home/hadoop/eclipse/RingORAM/serverData/bucket_";
}
