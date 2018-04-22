package com.ringoram;

import java.util.Arrays;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;

public class ByteSerialize {
	/*
	 * transform object to byte array
	 * transform byte array to object
	 */
	public byte[] metadataSerialize(BucketMetadata meta){
		byte[] read_counter_bytes = Ints.toByteArray(meta.getRead_counter());
		int[] meta_buf = meta.getMeta_buf();
		byte[] meta_buf_bytes = Ints.toByteArray(meta_buf[0]);
		for(int i=1;i<meta_buf.length;i++){
			meta_buf_bytes = Bytes.concat(meta_buf_bytes,Ints.toByteArray(meta_buf[i]));
		}
		byte[] returndata = Bytes.concat(read_counter_bytes,meta_buf_bytes,meta.getValid_bits());
		read_counter_bytes = null;
		meta_buf = null;
		meta_buf_bytes = null;
		return returndata;
	}
	
	public BucketMetadata metadataFromSerialize(byte[] metaBytes){
		int startIndex = 0;
		int read_counter = Ints.fromByteArray(Arrays.copyOfRange(metaBytes, startIndex, startIndex+4));
		startIndex += 4;
		byte[] meta_buf_bytes = Arrays.copyOfRange(metaBytes, startIndex, startIndex+Configs.METADATA_BYTES_LEN);
		int[] meta_buf = new int[Configs.REAL_BLOCK_COUNT+Configs.Z];
		for(int i=0;i<meta_buf.length;i++){
			meta_buf[i] = Ints.fromByteArray(Arrays.copyOfRange(meta_buf_bytes, i*4, (i+1)*4));
		}
		startIndex += 4*meta_buf.length;
		byte[] valid_bits = Arrays.copyOfRange(metaBytes, startIndex, startIndex+Configs.Z);
		BucketMetadata meta = new BucketMetadata(read_counter,meta_buf,valid_bits);
		meta_buf_bytes = null;
		return meta;
	}
	
	public byte[] bucketSerialize(Bucket bucket){
		byte[] bucket_id_bytes = Ints.toByteArray(bucket.getId());
		byte[] meta_bytes = metadataSerialize(bucket.getBucket_meta());		
		byte[] returndata = Bytes.concat(bucket_id_bytes,meta_bytes,bucket.getBucket_data());
		return returndata;
		
	}
	
	public Bucket bucketFromSerialize(byte[] serialized){
		int startIndex = 0;
		int id = Ints.fromByteArray(Arrays.copyOfRange(serialized, startIndex, 4));
		startIndex += 4;
		byte[] meta_bytes = Arrays.copyOfRange(serialized, startIndex, startIndex+Configs.METADATA_BYTES_LEN);
		BucketMetadata meta = metadataFromSerialize(meta_bytes);
		startIndex += Configs.METADATA_BYTES_LEN;
		byte[] bucketData = Arrays.copyOfRange(serialized, startIndex, startIndex+Configs.BLOCK_DATA_LEN*Configs.Z);
		Bucket bucket = new Bucket(id,bucketData,meta);
		return bucket;
	}
}

