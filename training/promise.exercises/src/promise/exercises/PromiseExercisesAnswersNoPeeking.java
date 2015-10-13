package promise.exercises;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import com.servicemesh.core.async.CompletablePromise;
import com.servicemesh.core.async.Function;
import com.servicemesh.core.async.Promise;
import com.servicemesh.core.async.PromiseFactory;

public class PromiseExercisesAnswersNoPeeking {

	/**
	 * Use Promise.pure to return a constant value. The Expected response is 'true'
	 */
	public static Promise<Boolean> pureConstant() {
		return Promise.pure(true);
	}
	
	/**
	 * Use the Map method to convert a String array to a single string of words asynchronously.
	 * Don't worry about separating the words with commas or spaces.
	 */
	public static Promise<String> mapArrayToString(Promise<String[]> wordsPromise) {
		return wordsPromise.map(new Function<String[], String>() {

			@Override
			public String invoke(String[] words) {
				String s = new String();
				for (String w : words) {
					s = s + w;
				}
				return s;
			}
		});
	}
	
	/**
	 * Use the FlatMap method to collapse the result lists from promise1 and promise2 into 1 list.
	 */
	public static Promise<List<String>> flatMapCollapseLists(Promise<List<String>> promise1 , final Promise<List<String>> promise2) {
		return promise1.flatMap(new Function<List<String>, Promise<List<String>>>() {
            @Override
            public Promise<List<String>> invoke(final List<String> result1)
            {
                return promise2.map(new Function<List<String>,List<String>>() {

					@Override
					public List<String> invoke(List<String> result2) {
						List<String> collected = new LinkedList<String>();
						collected.addAll(result1);
                        collected.addAll(result2);
						return collected;
					}
                	
                });
            }
		});
	}
	
	/**
	 * Use the Sequence and map methods to add up the values of a list of promises asynchronously.
	 */
	public static Promise<Integer> sequenceAddition(List<Promise<Integer>> promises) {
		Promise<List<Integer>> proms = Promise.sequence(promises);
		return proms.map(new Function<List<Integer>,Integer>() {

			@Override
			public Integer invoke(List<Integer> ints) {
				int i = 0;
				for (Integer Int : ints) {
					i = i + Int.intValue();
				}
				return i;
			}
			
		});
	}
	
	public static void main(String[] args) throws Throwable {
		// call the pureConstant method
		Promise<Boolean> purePromise = pureConstant();
		Boolean pureOut = purePromise.get();
		if (pureOut.booleanValue()) {
			System.out.println("pureConstant - Passes.");
		} else {
			System.out.println("pureConstant - Fails.");
		}
		
		
		// call the mapArrayToString method
		String[] words = {"services","are","fun"};
		Promise<String[]> mapInput = Promise.pure(words);
		Promise<String> mapOutput = mapArrayToString(mapInput);
		String mapOut = mapOutput.get();
		if (mapOut.equals("servicesarefun")) {
			System.out.println("mapArrayToString - Passes.");
		} else {
			System.out.println("mapArrayToString - Fails.");
		}
		
		
		// call the flatMapCollapseLists method.
        CompletablePromise<List<String>> promise1 = PromiseFactory.create();
        CompletablePromise<List<String>> promise2 = PromiseFactory.create();
		String[] array1 = { "one", "two", "three" };
        promise1.complete(Arrays.asList(array1));
        String[] array2 = { "four", "five", "six" };
        promise2.complete(Arrays.asList(array2));
        Promise<List<String>> flattened = flatMapCollapseLists(promise1, promise2);
        List<String> allTogether = flattened.get();
        if (allTogether.containsAll(Arrays.asList(array1)) 
        	&& allTogether.containsAll(Arrays.asList(array2))) {
			System.out.println("flatMapCollapseLists - Passes.");
		} else {
			System.out.println("flatMapCollapseLists - Fails.");
		}
		
		
		// call the sequenceAddition method. This shows an example of using CompletablePromises
		LinkedList<Promise<Integer>> promises = new LinkedList<Promise<Integer>>();
		CompletablePromise<Integer> p1 = PromiseFactory.create();
		CompletablePromise<Integer> p2 = PromiseFactory.create();
		CompletablePromise<Integer> p3 = PromiseFactory.create();
		CompletablePromise<Integer> p4 = PromiseFactory.create();
		promises.add(p1);
		promises.add(p2);
		promises.add(p3);
		promises.add(p4);
		p3.complete(3);
		Promise<Integer> additionResult = sequenceAddition(promises);
		p1.complete(1);
		p4.complete(4);
		p2.complete(2);
		Integer addOut = additionResult.get();
		if (addOut.intValue() == 10) {
			System.out.println("sequenceAddition - Passes.");
		} else {
			System.out.println("sequenceAddition - Fails.");
		}
		
	}

}
