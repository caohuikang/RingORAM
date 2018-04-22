package com.ringoram;

import java.io.Serializable;

public class Bucket implements Serializable{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	int id;//bucket id in the tree, root is 0
	byte[] bucket_data;//all block data in the bucket
	BucketMetadata bucket_meta;//bucket meta data
	
	public Bucket(){
		this.id = 0;
		this.bucket_data = new byte[Configs.BLOCK_DATA_LEN * Configs.Z];
		this.bucket_meta = new BucketMetadata();
	}
	
	public Bucket(int id, byte[] bucket_data, BucketMetadata meta){
		this.id = id;
		this.bucket_data = bucket_data;
		this.bucket_meta = meta;
	}
	
	//get block data from the bucket data
	public byte[] getBlock(int offset){
		int startIndex = offset * Configs.BLOCK_DATA_LEN;
		byte[] returndata = new byte[Configs.BLOCK_DATA_LEN];
		for(int i=0;i<Configs.BLOCK_DATA_LEN;i++){
			returndata[i] = bucket_data[startIndex+i];
		}
		return returndata;
	}
	
	//set the valid bit to 0 to make the block can't be read until next shuffle
	public void reset_valid_bits(int index){
		bucket_meta.set_meta_validbit(index);
	}
	
	//every time the bucket be accessed, add the read count
	public void add_read_counter(){
		bucket_meta.add_meta_readcounter();
	}
	
	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id = id;
	}
	public byte[] getBucket_data() {
		return bucket_data;
	}
	public void setBucket_data(byte[] bucket_data) {
		this.bucket_data = bucket_data;
	}
	public BucketMetadata getBucket_meta() {
		return bucket_meta;
	}
	public void setBucket_meta(BucketMetadata bucket_meta) {
		this.bucket_meta = bucket_meta;
	}

}
