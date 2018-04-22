package com.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.ringoram.*;
import com.ringoram.Configs.OPERATION;

public class Client implements ClientInterface{

	private static int requestID = 0;
	
	protected InetSocketAddress serverAddress;
	protected AsynchronousChannelGroup mThreadGroup;
	protected AsynchronousSocketChannel mChannel;

	private int evict_count;
	private int evict_g;
	private int[] position_map;
	
	Stash stash;
	ByteSerialize seria;
	MathUtility math;
	
	@SuppressWarnings("rawtypes")
	public Client() {
		this.evict_count = 0;
		this.evict_g = 0;
		this.position_map = new int[Configs.BLOCK_COUNT];
		this.stash = new Stash();
		this.seria = new ByteSerialize();
		this.math = new MathUtility();
		//when initializing, assign all block a random path id
		for(int i=0;i<Configs.BLOCK_COUNT;i++){
			this.position_map[i] = math.getRandomLeaf() + Configs.LEAF_START;
		}
		// connect to server
		try {
			serverAddress = new InetSocketAddress(Configs.SERVER_HOSTNAME, Configs.SERVER_PORT);
			mThreadGroup = AsynchronousChannelGroup.withFixedThreadPool(Configs.THREAD_FIXED,
					Executors.defaultThreadFactory());
			mChannel = AsynchronousSocketChannel.open(mThreadGroup);
			Future connection = mChannel.connect(serverAddress);
			connection.get();
			System.out.println("client connect to server successful!!");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void initServer(){
		ByteBuffer header = MessageUtility.createMessageHeaderBuffer(MessageUtility.ORAM_INIT, 0);
		byte[] responseBytes = sendAndGetMessage(header, MessageUtility.ORAM_INIT);
		System.out.println("client INIT server successful!" + responseBytes[0]);
		responseBytes = null;
	}
	
	/* core operation in ring oram
	 * @param blockIndex: block unique index
	 * @param op: request operation, read or write
	 * @param newdata: when operation is write, newdata is the data that you want to write
	 */
	public byte[] oblivious_access(int blockIndex, OPERATION op, byte[] newdata){
		requestID ++;
		System.out.println("Process request "+requestID);
		
		byte[] readData = null;//return data
		
		//get leaf id and update a new random leaf id
		int position = position_map[blockIndex];
		int position_new = math.getRandomLeaf() + Configs.LEAF_START;
		position_map[blockIndex] = position_new;
		
		//read block from server, and insert into the stash
		read_path(position, blockIndex);
		//find block from the stash
		Block block = stash.find_by_blockIndex(blockIndex);
		
		if(op == OPERATION.ORAM_ACCESS_WRITE){
			if(block==null){//not in the stash
				//System.out.println("when write, can't find block in the stash");
				//new block and add it to the stash
				block = new Block(blockIndex,position_new,newdata);
				stash.add(block);
			}else{//find in the stash, update stash block
				block.setData(newdata);
				block.setLeaf_id(position_new);
			/*	if(stash.find_by_address(blockIndex)==block)
					System.out.println("find block in stash and update successful!");
				else
					System.out.println("find block in the stash and update fail!");*/
			}
			readData = block.getData();
		}
		if(op == OPERATION.ORAM_ACCESS_READ){
			if(block != null){//find block in the stash or servere
				System.out.println("when read block "+blockIndex+" find block in the stash.");
				readData = block.getData();
			}
		}
		
		evict_count = (evict_count+1)%Configs.SHUFFLE_RATE;
		//evict count reaches shuffle rate, evict path
		if(evict_count == 0){
			evict_path(math.gen_reverse_lexicographic(evict_g, Configs.BUCKET_COUNT, Configs.HEIGHT));
			evict_g = (evict_g+1)%Configs.LEAF_COUNT;
		}
		
		//early re-shuffle current path
		BucketMetadata[] meta_list = get_metadata(position);
		early_reshuffle(position, meta_list);
		
		return readData;
	}
	
	@Override
	public void read_path(int pathID, int blockIndex) {
		// TODO Auto-generated method stub
		//get meta data of the buckets in the path
		BucketMetadata[] meta_list = get_metadata(pathID);
		//read proper block in the path
		read_block(pathID, blockIndex,meta_list);
	
	}
	
	public BucketMetadata[] get_metadata(int pathID){
		byte[] pos = Ints.toByteArray(pathID);
		byte[] header = MessageUtility.createMessageHeaderBytes(MessageUtility.ORAM_GETMETA,pos.length);
		ByteBuffer requestBuffer = ByteBuffer.wrap(Bytes.concat(header,pos));
		byte[] responseBytes = sendAndGetMessage(requestBuffer, MessageUtility.ORAM_GETMETA);
		
		//full binary tree, so the meta data count is tree height
		BucketMetadata[] meta_list = new BucketMetadata[Configs.HEIGHT];
		int startIndex = 0;
		int index = 0;
		//recover meta data from the responseBytes
		for(int pos_run = pathID; pos_run >= 0; pos_run = (pos_run - 1) >> 1){
			byte[] meta_bytes = Arrays.copyOfRange(responseBytes, startIndex, startIndex+Configs.METADATA_BYTES_LEN);
			meta_list[index] = seria.metadataFromSerialize(meta_bytes);
			startIndex += Configs.METADATA_BYTES_LEN;
			index++;
			
			if(pos_run == 0)
				break;
		}
		responseBytes = null;
		return meta_list;
	}
	
	public void read_block(int pathID,int blockIndex,BucketMetadata[] meta_list){
		
		boolean found = false;// record if the block is in the path
		//offset in the bucket data of the block that will be read from the server
		int[] read_offset = new int[Configs.HEIGHT];
		
		for (int i = 0, pos_run = pathID;pos_run>=0; pos_run = (pos_run - 1) >> 1, i++) {
			if(found){//if found the block, then read a dummy block
				read_offset[i] = math.get_random_dummy(meta_list[i].getValid_bits(), meta_list[i].get_offset());
			}else{//not found the block
				for(int j=0;j<Configs.REAL_BLOCK_COUNT;j++){
					int offset = meta_list[i].get_offset()[j];
					if((meta_list[i].get_block_index()[j]==blockIndex) && 
							(meta_list[i].getValid_bits()[offset]==1)){//block is in this bucket
						read_offset[i] = offset;
						found = true;
					}
					if(found)
						break;
				}
				if(!found){//block is not in this bucket
					read_offset[i] = math.get_random_dummy(meta_list[i].getValid_bits(), meta_list[i].get_offset());
				}
			}
			if(pos_run == 0)
				break;
		}
		
		//transform offset from int array to byte array
		byte[][] read_offset_2d_bytes = new byte[Configs.HEIGHT][];		
		for(int i=0;i<Configs.HEIGHT;i++){
			read_offset_2d_bytes[i] = Ints.toByteArray(read_offset[i]);
		}
		byte[] read_offset_bytes = read_offset_2d_bytes[0];
		for(int i=1;i<Configs.HEIGHT;i++){
			read_offset_bytes = Bytes.concat(read_offset_bytes,read_offset_2d_bytes[i]);
		}
		//send massage to server
		byte[] pos_bytes = Ints.toByteArray(pathID);
		byte[] requestBytes = Bytes.concat(pos_bytes,read_offset_bytes);
		byte[] header = MessageUtility.createMessageHeaderBytes(MessageUtility.ORAM_READBLOCK, requestBytes.length);
		ByteBuffer requestBuffer = ByteBuffer.wrap(Bytes.concat(header,requestBytes));
		byte[] responseBytes = sendAndGetMessage(requestBuffer,MessageUtility.ORAM_READBLOCK);
		if(found){//add to stash
			Block blk = new Block(blockIndex,pathID,responseBytes);
			stash.add(blk);
		}
	}
	
	@Override
	public void evict_path(int pathID){
		//read path from server
		for (int pos_run = pathID;pos_run>=0;pos_run = (pos_run - 1) >> 1) {
	        read_bucket(pos_run);
	        if (pos_run == 0)
	            break;
	    }
		//write path to server
		for (int pos_run = pathID;pos_run>=0;pos_run = (pos_run - 1) >> 1) {
	        write_bucket(pos_run);
	        if (pos_run == 0)
	            break;
	    }
	}
	
	public void read_bucket(int bucket_id){
		//send request to server
		byte[] bucket_id_bytes = Ints.toByteArray(bucket_id);
		byte[] header = MessageUtility.createMessageHeaderBytes(
				MessageUtility.ORAM_READBUCKET, bucket_id_bytes.length);
		ByteBuffer requestBuffer = ByteBuffer.wrap(Bytes.concat(header,bucket_id_bytes));
		byte[] responseBytes = sendAndGetMessage(requestBuffer,MessageUtility.ORAM_READBUCKET);
		
		//recover bucket from responseBytes
		Bucket bucket = seria.bucketFromSerialize(responseBytes);
		BucketMetadata meta = bucket.getBucket_meta();
		int[] block_index = meta.get_block_index();
		int[] offset = meta.get_offset();
		byte[] valid_bits = meta.getValid_bits();
		for(int i=0;i<Configs.REAL_BLOCK_COUNT;i++){
			//real block and not been accessed before, add to the stash
			if((block_index[i]>=0) && (valid_bits[offset[i]]==(byte)1)){
				byte[] block_data = bucket.getBlock(offset[i]);
				stash.add(new Block(block_index[i],position_map[block_index[i]],block_data));
			}
		}
	}
	
	public void write_bucket(int bucket_id){
		BucketMetadata meta = new BucketMetadata();
		Block[] block_list = new Block[Configs.REAL_BLOCK_COUNT];
		
		//get the proper block that can place in the bucket to the block_list, 
		//count record the real block count
		int count = stash.remove_by_bucket(bucket_id, Configs.REAL_BLOCK_COUNT, block_list);
		
		//shuffle the block data offset in bucket data
		meta.set_offset(math.get_random_permutation(Configs.Z));
		int[] offset = meta.get_offset();
		byte[] bucket_data = new byte[Configs.Z*Configs.BLOCK_DATA_LEN];
		for(int i=0;i<count;i++){
			int offset_i = offset[i]*Configs.BLOCK_DATA_LEN;
			byte[] block_data = block_list[i].getData();
			for(int j=0;j<Configs.BLOCK_DATA_LEN;j++){
				bucket_data[offset_i+j] = block_data[j];
			}
			meta.set_blockIndex_bit(i, block_list[i].getBlockIndex());
		}
		//full fill bucket
		for(int i = count;i<Configs.Z;i++){
			int offset_i = offset[i]*Configs.BLOCK_DATA_LEN;
			for(int j=0;j<Configs.BLOCK_DATA_LEN;j++){
				bucket_data[offset_i+j] = 0;
			}
			if(i<Configs.REAL_BLOCK_COUNT){//dummy block to fill real block space
				meta.set_blockIndex_bit(i, -1);
			}
		}
		Bucket bucket = new Bucket(bucket_id,bucket_data,meta);
		byte[] bucket_bytes = seria.bucketSerialize(bucket);
		byte[] header = MessageUtility.createMessageHeaderBytes(
				MessageUtility.ORAM_WRITEBUCKET, bucket_bytes.length);
		ByteBuffer requestBuffer = ByteBuffer.wrap(Bytes.concat(header,bucket_bytes));
	//	byte[] responseBytes = sendAndGetMessage(requestBuffer,MessageUtility.ORAM_WRITEBUCKET);
	//	System.out.println("In shuffer, client WRITEBUCKET successful!"+responseBytes[0]);
		sendAndGetMessage(requestBuffer,MessageUtility.ORAM_WRITEBUCKET);
		block_list = null;
		bucket_data = null;
		offset = null;
		bucket = null;
		bucket_bytes = null;
		requestBuffer = null;
		//responseBytes = null;
	}
	
	@Override
	public void early_reshuffle(int pathID, BucketMetadata[] meta_list){
		//shuffle bucket in the path
		for (int pos_run = pathID, i = 0;pos_run>=0;pos_run = (pos_run - 1) >> 1, i++) {
	        if (meta_list[i].getRead_counter() >= (Configs.DUMMY_BLOCK_COUNT-2)) {
	           System.out.println("early reshuffle in pos " +pos_run );
	            read_bucket(pos_run);
	            write_bucket(pos_run);
	        }
	        if (pos_run == 0)
	            break;
	    }
	}
	
	@SuppressWarnings("rawtypes")
	public byte[] sendAndGetMessage(ByteBuffer requestBuffer, int messageType) {
		byte[] responseBytes = null;
		//send request to server
		try {
			while (requestBuffer.remaining() > 0) {
				Future requestBufferRead = mChannel.write(requestBuffer);
				try {
					requestBufferRead.get();
				} catch (InterruptedException | ExecutionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

			// get response from server
			ByteBuffer typeAndSize = ByteBuffer.allocate(4 + 4);
			Future typeAndSizeRead = mChannel.read(typeAndSize);
			try {
				typeAndSizeRead.get();
			} catch (InterruptedException | ExecutionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			typeAndSize.flip();
			int[] typeAndSizeInt = MessageUtility.parseTypeAndLength(typeAndSize);
			int type = typeAndSizeInt[0];//message type
			int size = typeAndSizeInt[1];//data size
			typeAndSize = null;

			ByteBuffer responseBuffer = ByteBuffer.allocate(size);
			while (responseBuffer.remaining() > 0) {
				Future responseBufferRead = mChannel.read(responseBuffer);
				responseBufferRead.get();
			}
			responseBuffer.flip();
			//check if it is current request corresponding response
			if (type == messageType) {
				responseBytes = new byte[size];
				responseBuffer.get(responseBytes);//read data into byte array
			} else {
				System.out.println("client get wrong when resieve response from server!");
			}
			responseBuffer = null;
		} catch (Exception e) {
			try {
				mChannel.close();
			} catch (IOException e1) {
			}
		}
		return responseBytes;
	}
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		Client client = new Client();
		client.initServer();
		for(int i=0;i<4;i++){
			byte[] data = new byte[Configs.BLOCK_DATA_LEN];
			Arrays.fill(data, (byte)i);
			client.oblivious_access(i, OPERATION.ORAM_ACCESS_WRITE, data);
		}
		byte[] newdata = new byte[Configs.BLOCK_DATA_LEN];
		Arrays.fill(newdata, (byte)12);
		client.oblivious_access(3, OPERATION.ORAM_ACCESS_WRITE, newdata);
		for(int i=0;i<4;i++){
			byte[] data = new byte[Configs.BLOCK_DATA_LEN];
			//Arrays.fill(data, (byte)1);
			data = client.oblivious_access(i, OPERATION.ORAM_ACCESS_READ, data);
			if(data != null){
				System.out.println("block "+i+" data:");
				for(int j=0;j<Configs.BLOCK_DATA_LEN;j++){
					System.out.print(data[j]+" ");
				}
				System.out.println();
			}else{
				System.out.println("can't find block "+i+" in server storage");
			}
		}
	}

}
