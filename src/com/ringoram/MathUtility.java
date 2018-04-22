package com.ringoram;

import java.util.Random;

public class MathUtility {
	
	Random rnd_generator;
	
	public MathUtility(){
		this.rnd_generator = new Random();
	}
	
	public int getRandomLeaf(){
		int randomLeaf = rnd_generator.nextInt(Configs.LEAF_COUNT);
		return randomLeaf;
	}
	
	public int get_random(int range) {//return randombytes_uniform(range);
		return rnd_generator.nextInt(range);	
	 }
	
	public  int[] get_random_permutation(int len) {
		int permutation[] = new int[len];
	    Two_random random_list[] = new Two_random[len];
	    int i;
	    for (i = 0; i < len; i++) {
	    	Two_random ran = new Two_random();
	        ran.random = get_random(len << 7);
	        ran.no = i;
	        random_list[i] = ran;
	    }
	    QuickSort quicksort=new QuickSort();
	    quicksort.quickSorting(random_list);
	    for (i = 0; i < len; i++){
	        permutation[i] = random_list[i].no;
	    }
	    return permutation;
	}
	
	public int get_random_dummy(byte valid_bits[], int offsets[]) {
	    for (int i = Configs.REAL_BLOCK_COUNT;i < (Configs.Z);i++) {
	        if (valid_bits[offsets[i]] == (byte)1){
	            return offsets[i];
	        }
	    }
	    System.out.println("not enough dummy\n");
	    return -1;//when there is not enough dummy,will except java.lang.ArrayIndexOutOfBoundsException: -32
	}
	
	public int gen_reverse_lexicographic(int g, int bucket_count, int tree_height) {
	    int i, pos = 0;
	    for (i = 0;i < tree_height - 1;i++) {
	        pos = pos * 2 + (g & 0x01) + 1;
	        g >>= 1;
	    }
	    if (pos >= bucket_count)
	        pos = (pos - 1) >> 1;
	    if(pos>=0) {return pos;}
	    else {System.out.println("pos compute in gen_reverse_lexicographic has wrong!"); return 0;}
	}
	
}
