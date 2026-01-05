import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.TreeSet;

public class Permutations implements Iterator<ArrayList<Integer>>, Iterable<ArrayList<Integer>> {
  private static final int MAX_EXACT_DEPTH = 5;
  private static final int MAX_PERMUTATIONS = 500_000;
  private static final int MAGIC_NULL_REPRESENTATION = -1;
  private final TreeSet<Integer[]> resultSet = new TreeSet<>(Arrays::compare);
  private final ArrayList<ArrayList<Integer>> resultList = new ArrayList<>();
  private int currentIndex = 0;

  public Permutations permute(Integer[] nums) {
    permuteHelper(nums, 0, 0);
    for (Integer[] arr : resultSet) {
      ArrayList<Integer> list = new ArrayList<>();
      for (Integer num : arr) {
        list.add(num == MAGIC_NULL_REPRESENTATION ? null : num);
      }
      resultList.add(list);
    }
    return this;
  }

  private void permuteHelper(Integer[] nums, int index, int depth) {
    if (resultSet.size() >= MAX_PERMUTATIONS) {
      return;
    }

    // Base case: If index reaches the end, add the current permutation
    if (index == nums.length) {
      Integer[] currentPermutation = Arrays.copyOf(nums, nums.length);
      for (int i = 0; i < currentPermutation.length; i++) {
        if (currentPermutation[i] == null) {
          currentPermutation[i] = MAGIC_NULL_REPRESENTATION;
        }
      }
      resultSet.add(currentPermutation);
      return;
    }

    if (depth < MAX_EXACT_DEPTH) {
      for (int i = index; i < nums.length; i++) {
        // Swap elements at 'index' and 'i'
        swap(nums, index, i);
        // Recurse for the next element
        permuteHelper(nums, index + 1, depth + 1);
        // Backtrack: Swap back to original state
        swap(nums, index, i);
      }
    } else {
      // Randomly select an index to swap with
      // This helps in generating diverse permutations without deep recursion
      int i = (int) (Math.random() * (nums.length - index) + index);
      swap(nums, index, i);
      permuteHelper(nums, index + 1, depth + 1);
      swap(nums, index, i);
    }
  }

  private void swap(Integer[] nums, int i, int j) {
    Integer temp = nums[i];
    nums[i] = nums[j];
    nums[j] = temp;
  }

  @Override
  public boolean hasNext() {
    return currentIndex < resultList.size();
  }

  @Override
  public ArrayList<Integer> next() {
    return resultList.get(currentIndex++);
  }

  @Override
  public Iterator<ArrayList<Integer>> iterator() {
    return this;
  }

  public ArrayList<ArrayList<Integer>> getAllPermutations() {
    return new ArrayList<>(resultList);
  }

  public ArrayList<Integer> getIthPermutation(int i) {
    if (i < 0 || i >= resultList.size()) {
      throw new IndexOutOfBoundsException("Index: " + i + ", Size: " + resultList.size());
    }
    return resultList.get(i);
  }
}
