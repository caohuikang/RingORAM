package com.ringoram;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
/*
 * stash in the client
 * @param stash_hash: hash the block to the map
 * @param stash_list: relate block to bucket
 * @param counter: block count that can place into bucket
 */
public class Stash {
	private Map<Integer,Block> stash_hash;
	private List<List<Block>> stash_list;
	private int[] counter;
	
	public Stash(){
		this.counter = new int[Configs.BUCKET_COUNT];
		this.stash_hash = new HashMap<Integer,Block>();
		this.stash_list = new ArrayList<List<Block>>();
		for(int i=0;i<Configs.BUCKET_COUNT;i++){
			List<Block> bucket_i = new ArrayList<Block>();
			this.stash_list.add(bucket_i);
		}
	}
	
	public void add(Block blk){
		if(!stash_hash.containsKey(blk.getBlockIndex())){
			stash_hash.put(blk.getBlockIndex(), blk);
			stash_list.get(blk.getLeaf_id()).add(blk);
			for (int pos = blk.getLeaf_id();pos>=0;pos = (pos - 1) >> 1) {
				//bucket in path counter++ because the block can place into this bucket
	            counter[pos]++;
	            if (pos == 0)
	                break;
	        }
		}
	}
	
	public Block find_by_blockIndex(int blockIndex){
		if(!stash_hash.containsKey(blockIndex)){
	        return null;
	    }
	    return stash_hash.get(blockIndex);
	}
	
	//remove proper blocks in stash that can place into the bucket to block_list
	public int remove_by_bucket(int bucket_id, int max, Block[] block_list){
		int remove = remove_by_bucket_helper(bucket_id, max, 0, block_list);
		return remove;
	}
	public int remove_by_bucket_helper(int bucket_id, int len, int start, Block[] block_list){
		int delete_now=0;
	    int delete_max=0;
	    Block block;
	    if (bucket_id >=Configs.BUCKET_COUNT || counter[bucket_id]<=0) {
	        return 0;
	    }
	    if (bucket_id >= Configs.LEAF_START) {
	        delete_now = counter[bucket_id];
	        delete_max = min(delete_now, len);
	        for (int j = 0;j < delete_max;j++) {
	            block = stash_list.get(bucket_id).get(0);
	            stash_list.get(bucket_id).remove(0);
	            block_list[start++] = block;
	            stash_hash.remove(block.getBlockIndex());
	            for (int pos_run = bucket_id;pos_run>=0; pos_run = (pos_run - 1) >> 1) {
	                counter[pos_run]--;
	                if (pos_run == 0)
	                    break;
	            }
	        }
	        return delete_max;
	    }
	    int random = get_random(2);
	    int left, right;
	    if (random > 0) {
	        left = 2 * bucket_id + 1;
	        right = 2 * bucket_id + 2;
	    } else {
	        left = 2 * bucket_id + 2;
	        right = 2 * bucket_id + 1;
	    }


	    int add = remove_by_bucket_helper(left, len, start, block_list);
	    len -= add;
	    start += add;
	    if (len == 0)
	        return add;
	    return remove_by_bucket_helper(right, len, start, block_list) + add;
	}
	public int min(int a,int b){
		int min = 0;
		min = (((a) < (b)) ? (a):(b));
		return min;
	}
	public int get_random(int range) {//return randombytes_uniform(range);
		Random ra =new Random();
		return ra.nextInt(range);	
	 }
	
	public Map<Integer, Block> getStash_hash() {
		return stash_hash;
	}
	public void setStash_hash(Map<Integer, Block> stash_hash) {
		this.stash_hash = stash_hash;
	}
	public List<List<Block>> getStash_list() {
		return stash_list;
	}
	public void setStash_list(List<List<Block>> stash_list) {
		this.stash_list = stash_list;
	}
	public int[] getCount() {
		return counter;
	}
	public void setCount(int[] counter) {
		this.counter = counter;
	}

}
