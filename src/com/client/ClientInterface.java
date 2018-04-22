package com.client;

import com.ringoram.BucketMetadata;

public interface ClientInterface {

	/*when read or write block, first read path from server
	 * @param pathID: path that need to get from server
	 * @param blockIndex: block that want to request
	 */
	public void read_path(int pathID, int blockIndex);
	
	//when request time reaches shuffle rate, evict path
	public void evict_path(int pathID);
	
	//when block in bucket almost all accessed, early re-shuffle the bucket
	public void early_reshuffle(int pathID, BucketMetadata[] meta_list);
}
