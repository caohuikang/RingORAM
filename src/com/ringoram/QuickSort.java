package com.ringoram;

public class QuickSort {
	public void quickSorting(Two_random[] arr){
	    qsort(arr, 0, arr.length-1);
	}
	private void qsort(Two_random[] arr, int low, int high){
	    if (low < high){
	        int pivot=partition(arr, low, high);    
	        qsort(arr, low, pivot-1);                   
	        qsort(arr, pivot+1, high);                
	    }
	}
	private int partition(Two_random[] arr, int low, int high){
	    Two_random pivot = arr[low];    
	    while (low<high){
	        while (low<high && arr[high].random>=pivot.random) --high;
	        arr[low]=arr[high];           
	        while (low<high && arr[low].random<=pivot.random) ++low;
	        arr[high] = arr[low];          
	    }
	    arr[low] = pivot;
	    return low;
	}
}
