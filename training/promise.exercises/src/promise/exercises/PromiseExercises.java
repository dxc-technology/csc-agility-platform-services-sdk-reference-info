package promise.exercises;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import com.servicemesh.core.async.CompletablePromise;
import com.servicemesh.core.async.Function;
import com.servicemesh.core.async.Promise;
import com.servicemesh.core.async.PromiseFactory;

/**
 * TODO If you are using eclipse to edit or build this project make sure you
 * create a classpath variable named LIB that points to the lib directory under
 * agility-platform-services-sdk 
 * 
 * Right click on your project. Click on Build Path -> Configure Build Path
 * Click Add Variable
 * Click Configure Variables...
 * Click New...
 * Set the name of the variable to LIB
 * Set the path of the variable to point to the lib folder in agility-platform-services-sdk
 *
 */

public class PromiseExercises {

	/**
	 * Use Promise.pure to return a constant value. The Expected response is 'true'
	 */
	public static Promise<Boolean> pureConstant() {
		return null;
	}

	/**
	 * Use the Map method to convert a String array to a single string of words asynchronously.
	 * Don't worry about separating the words with commas or spaces.
	 */
	public static Promise<String> mapArrayToString(Promise<String[]> wordsPromise) {
		return null;
	}

	/**
	 * Use the FlatMap method to collapse the result lists from promise1 and promise2 into 1 list.
	 */
	public static Promise<List<String>> flatMapCollapseLists(Promise<List<String>> promise1,
			final Promise<List<String>> promise2) {
		return null;
	}

	/**
	 * Use the Sequence and map methods to add up the values of a list of promises asynchronously.
	 */
	public static Promise<Integer> sequenceAddition(List<Promise<Integer>> promises) {
		return null;
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
		String[] words = { "services", "are", "fun" };
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
		if (allTogether.containsAll(Arrays.asList(array1)) && allTogether.containsAll(Arrays.asList(array2))) {
			System.out.println("flatMapCollapseLists - Passes.");
		} else {
			System.out.println("flatMapCollapseLists - Fails.");
		}

		// call the sequenceAddition method. This shows an example of using
		// CompletablePromises
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
