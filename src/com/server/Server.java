package com.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.ringoram.*;

public class Server {

	MathUtility math;
	ServerStorage storage;
	ByteSerialize seria;
	
	boolean initedServer = false;
	
	public Server(){
		this.math = new MathUtility();
		this.storage = new ServerStorage();
		this.seria = new ByteSerialize();
	}
	
	public void run() {
		try {
			AsynchronousChannelGroup threadGroup = AsynchronousChannelGroup.withFixedThreadPool(Configs.THREAD_FIXED,
					Executors.defaultThreadFactory());
			AsynchronousServerSocketChannel channel = AsynchronousServerSocketChannel.open(threadGroup)
					.bind(new InetSocketAddress(Configs.SERVER_PORT));
			System.out.println("server wait for connection.");
			channel.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {

				@Override
				public void completed(AsynchronousSocketChannel serveClientChannel, Void att) {
					// TODO Auto-generated method stub
					// start listening for other connections
					channel.accept(null, this);
					// start up a new thread to serve this connection
					Runnable serializeProcedure = () -> serveClient(serveClientChannel);
					new Thread(serializeProcedure).start();
				}

				@Override
				public void failed(Throwable arg0, Void arg1) {
					// TODO Auto-generated method stub
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@SuppressWarnings("rawtypes")
	public void serveClient(AsynchronousSocketChannel mChannel) {
		try {
			while (true) {
				ByteBuffer messageTypeAndSize = ByteBuffer.allocate(4 + 4);
				Future messageRead = mChannel.read(messageTypeAndSize);
				messageRead.get();
				messageTypeAndSize.flip();
				int[] messageTypeAndSizeInt = MessageUtility.parseTypeAndLength(messageTypeAndSize);
				int type = messageTypeAndSizeInt[0];// message type
				int size = messageTypeAndSizeInt[1];// data size
				messageTypeAndSize = null;

				ByteBuffer message = null;
				if (size != 0) {
					// read rest of the message
					message = ByteBuffer.allocate(size);
					while (message.remaining() > 0) {
						Future entireRead = mChannel.read(message);
						entireRead.get();
					}
					message.flip();
				}

				byte[] serializedResponse = null;// response data
				byte[] responseHeader = null;// response message head

				if (type == MessageUtility.ORAM_INIT) {
					Lock lock = new ReentrantLock();
					lock.lock();
					try {
						if (!initedServer) {
							initServer();
						} else {
							System.out.println("server had been initialized!");
							lock.unlock();
							break;
						}
						initedServer = true;
					} catch (Exception e) {
						e.printStackTrace();
					} finally {
						lock.unlock();
					}
					byte[] responseBytes = { 1 };
					serializedResponse = responseBytes;
					responseHeader = MessageUtility.createMessageHeaderBytes(MessageUtility.ORAM_INIT,
							serializedResponse.length);
					System.out.println("server INIT successful!");
					System.out.println();
					responseBytes = null;
				}
				if(type == MessageUtility.ORAM_GETMETA){//get bucket meta data
					System.out.println("server processes GETMETA request.");
					byte[] pos_bytes = new byte[4];
					message.get(pos_bytes);
					int pos = Ints.fromByteArray(pos_bytes);
					
					//get bucket meta data from storage
					BucketMetadata[] meta_list = new BucketMetadata[Configs.HEIGHT];
					byte[][] meta_2d_bytes = new byte[Configs.HEIGHT][];
					int index = 0;
					for(int pos_run = pos; pos_run >= 0; pos_run = (pos_run - 1) >> 1){
						meta_list[index] = storage.get_bucket(pos_run).getBucket_meta();
						meta_2d_bytes[index] = seria.metadataSerialize(meta_list[index]);
						index++;
						
						if(pos_run == 0)
							break;
					}
					//transform meta data list into byte array
					byte[] meta_bytes = meta_2d_bytes[0];
					for(int i=1;i<Configs.HEIGHT;i++){
						meta_bytes = Bytes.concat(meta_bytes,meta_2d_bytes[i]);
					}
					serializedResponse = meta_bytes;
					responseHeader = MessageUtility.createMessageHeaderBytes(
							MessageUtility.ORAM_GETMETA, serializedResponse.length);
					
					pos_bytes = null;
					meta_list = null;
					meta_2d_bytes = null;
					meta_bytes = null;
				}
				if(type == MessageUtility.ORAM_READBLOCK){//read block
					System.out.println("server processes READBLOCK request.");
					byte[] pos_bytes = new byte[4];
					message.get(pos_bytes);
					int position = Ints.fromByteArray(pos_bytes);//path id
					byte[] read_offset_bytes = new byte[Configs.HEIGHT*4];
					message.get(read_offset_bytes);
					int[] read_offset = new int[Configs.HEIGHT];
					//block data offset in bucket data
					for(int i=0;i<Configs.HEIGHT;i++){
						read_offset[i] = Ints.fromByteArray(Arrays.copyOfRange(read_offset_bytes, i*4, (i+1)*4));
					}
					byte[] responseBytes = new byte[Configs.BLOCK_DATA_LEN];//all zero
					//read only one block from every bucket in the path
					for (int pos = position, i = 0;pos>=0;pos = (pos - 1) >> 1, i++) {
						Bucket bucket = storage.get_bucket(pos);
						byte[] block_data = bucket.getBlock(read_offset[i]);
						//can't read this block before next shuffle
						bucket.reset_valid_bits(read_offset[i]);
						bucket.add_read_counter();
						storage.set_bucket(pos, bucket);//update storage
						for(int j=0;j<Configs.BLOCK_DATA_LEN;j++){
							responseBytes[j] ^= block_data[j];//exclusive or
						}
						if(pos == 0)
							break;
					}
					
					serializedResponse = responseBytes;
					responseHeader = MessageUtility.createMessageHeaderBytes(
							MessageUtility.ORAM_READBLOCK, serializedResponse.length);
					
					responseBytes = null;				
				}
				if(type == MessageUtility.ORAM_READBUCKET){//read bucket
					byte[] bucket_id_bytes = new byte[4];
					message.get(bucket_id_bytes);
					int bucket_id = Ints.fromByteArray(bucket_id_bytes);
					
					Bucket bucket = storage.get_bucket(bucket_id);
					byte[] bucket_bytes = seria.bucketSerialize(bucket);
					serializedResponse = bucket_bytes;
					responseHeader = MessageUtility.createMessageHeaderBytes(
							MessageUtility.ORAM_READBUCKET, serializedResponse.length);
					bucket_id_bytes = null;
					bucket_bytes = null;
				}
				if(type == MessageUtility.ORAM_WRITEBUCKET){//write bucket
					int bucket_bytes_len = 4+Configs.Z*Configs.BLOCK_DATA_LEN+Configs.METADATA_BYTES_LEN;
					byte[] bucket_bytes = new byte[bucket_bytes_len];
					message.get(bucket_bytes);
					
					Bucket bucket = seria.bucketFromSerialize(bucket_bytes);
					storage.set_bucket(bucket.getId(), bucket);
				//	System.out.println("server WRITEBUCKET "+bucket.getId()+" successful!");
					byte[] responseBytes = {1};
					serializedResponse = responseBytes;
					responseHeader = MessageUtility.createMessageHeaderBytes(
							MessageUtility.ORAM_WRITEBUCKET, serializedResponse.length);
					bucket_bytes = null;
					bucket = null;
					responseBytes = null;
				}
				// send response to client
				ByteBuffer responseMessage = ByteBuffer.wrap(Bytes.concat(responseHeader, serializedResponse));
				while (responseMessage.remaining() > 0) {
					Future responseMessageRead = mChannel.write(responseMessage);
					responseMessageRead.get();
				}
			}
		} catch (Exception e) {
			try {
				mChannel.close();
			} catch (IOException e1) {

			}
		}
	}
	
	public void initServer(){
		for(int i=0;i<Configs.BUCKET_COUNT;i++){
			Bucket bucket = new Bucket();
			bucket.getBucket_meta().init_block_index();
			bucket.getBucket_meta().set_offset(math.get_random_permutation(Configs.Z));
			try {
				storage.set_bucket(i, bucket);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		Server server = new Server();
		server.run();
	}

}
