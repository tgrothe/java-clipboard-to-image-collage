import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Random;
import javax.imageio.ImageIO;

public class PlaceClipboardCollage {
  private record Permutation(Integer[] positions, int index, int area)
      implements Comparable<Permutation> {
    private static final Random RANDOM = new Random(12345);

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof Permutation that)) {
        return false;
      }
      return index == that.index && Arrays.equals(positions, that.positions);
    }

    @Override
    public int hashCode() {
      int result = Arrays.hashCode(positions);
      result = 31 * result + index;
      return result;
    }

    @Override
    public int compareTo(Permutation o) {
      return Integer.compare(o.area, area);
    }

    private Permutation createNewRandomizedPermutation(ArrayList<Image> images, int row_len) {
      int newIndex = index + 1;
      Integer[] newPositions = positions.clone();
      for (int i = newIndex; i < newPositions.length - 1; i++) {
        int swapIndex = RANDOM.nextInt(i, newPositions.length);
        Integer temp = newPositions[i];
        newPositions[i] = newPositions[swapIndex];
        newPositions[swapIndex] = temp;
      }
      return new Permutation(
          newPositions, newIndex, calculateArea(images, newPositions, row_len, newIndex));
    }
  }

  private static final int SPACE_D = 25;
  private static final int MAX_POLLS = 1_000_000;
  private static final int MAX_QUEUE_SIZE = 1000;

  public static void main(String[] args) throws IOException, InterruptedException {
    ArrayList<Image> images = new ArrayList<>();
    System.out.println(
        "The app is running. Copy images to clipboard to add them to the collage. Press Ctrl+C to stop.");
    while (true) {
      Image imageFromClipboard = getImageFromClipboard();
      if (imageFromClipboard != null) {
        System.out.println("Found image.");
        images.add(imageFromClipboard);
        StringSelection stringSelection = new StringSelection("");
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
        randomBFSAndSaveImages(images);
        System.out.println("Ready.");
      }
      Thread.sleep(1000);
    }
  }

  private static Image getImageFromClipboard() {
    try {
      Transferable transferable =
          Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
      if (transferable != null && transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) {
        return (Image) transferable.getTransferData(DataFlavor.imageFlavor);
      }
    } catch (Exception e) {
      System.out.println("Error getting image from clipboard: " + e.getMessage());
    }
    return null;
  }

  private static void randomBFSAndSaveImages(ArrayList<Image> images) throws IOException {
    final int row_len = images.size();
    Integer[] positions = new Integer[row_len * row_len];
    for (int i = 0; i < row_len; i++) {
      for (int j = 0; j < row_len; j++) {
        int index = i * row_len + j;
        positions[index] = index < images.size() ? index : null;
      }
    }

    int bestArea = Integer.MAX_VALUE;
    Integer[] bestPos = null;

    PriorityQueue<Permutation> queue = new PriorityQueue<>();
    HashSet<Permutation> visited = new HashSet<>();
    queue.add(
        new Permutation(positions, 0, calculateArea(images, positions, row_len, positions.length)));
    int polls = 0;
    int skips = 0;
    while (!queue.isEmpty()) {
      Permutation permutation = queue.poll();
      polls++;
      if (visited.contains(permutation)) {
        skips++;
        continue;
      }
      visited.add(permutation);
      int area = permutation.area();
      if (area >= bestArea) {
        skips++;
        continue;
      }
      if (permutation.index() == permutation.positions().length) {
        System.out.println("Found smaller area: " + area + " (polls: " + polls + ")");
        bestArea = area;
        bestPos = permutation.positions();
        continue;
      }
      if (polls >= MAX_POLLS) {
        continue;
      }
      int n = MAX_QUEUE_SIZE - queue.size();
      for (int i = 0; i < n; i++) {
        Permutation newPermutation = permutation.createNewRandomizedPermutation(images, row_len);
        if (visited.contains(newPermutation) || newPermutation.area() >= bestArea) {
          skips++;
          continue;
        }
        queue.add(newPermutation);
      }
    }
    System.out.println("Total permutations checked: " + polls);
    System.out.println("Total permutations skipped: " + skips);
    if (bestPos == null) {
      throw new IllegalStateException("No best position found");
    }

    // Sort each row to have a consistent output
    int[][] sortedRows = new int[row_len][row_len];
    for (int i = 0; i < row_len; i++) {
      for (int k = 0; k < row_len; k++) {
        sortedRows[i][k] =
            bestPos[i * row_len + k] == null ? Integer.MAX_VALUE : bestPos[i * row_len + k];
      }
      Arrays.sort(sortedRows[i]);
    }
    for (int i = 0; i < row_len; i++) {
      for (int k = 0; k < row_len; k++) {
        bestPos[i * row_len + k] = sortedRows[i][k] == Integer.MAX_VALUE ? null : sortedRows[i][k];
      }
    }

    saveImages(images, bestPos, bestArea, row_len);
  }

  private static int calculateArea(
      ArrayList<Image> images, Integer[] positions, int row_len, int stopAt) {
    int wmax = SPACE_D;
    int hmax = SPACE_D;
    a:
    for (int i = 0; i < row_len; i++) {
      int row_w = SPACE_D;
      int row_h = 0;
      for (int j = 0; j < row_len; j++) {
        if (i * row_len + j >= stopAt) {
          break a;
        }
        Integer p = positions[i * row_len + j];
        if (p != null) {
          Image image = images.get(p);
          int w = image.getWidth(null) + SPACE_D;
          int h = image.getHeight(null) + SPACE_D;
          row_w += w;
          row_h = Math.max(row_h, h);
        }
      }
      wmax = Math.max(wmax, row_w);
      hmax += row_h;
    }
    return Math.max(wmax, hmax);
  }

  private static int calculateRemainingArea(
      ArrayList<Image> images, Integer[] positions, int row_len, int startAt) {
    int wmax = 0;
    int hmax = 0;
    for (int i = 0; i < row_len; i++) {
      int row_w = 0;
      int row_h = 0;
      for (int j = 0; j < row_len; j++) {
        if (i * row_len + j < startAt) {
          continue;
        }
        Integer p = positions[i * row_len + j];
        if (p != null) {
          Image image = images.get(p);
          int w = image.getWidth(null) + SPACE_D;
          int h = image.getHeight(null) + SPACE_D;
          row_w += w;
          row_h = Math.max(row_h, h);
        }
      }
      wmax = Math.max(wmax, row_w);
      hmax += row_h;
    }
    return Math.max(wmax, hmax);
  }

  private static void saveImages(
      ArrayList<Image> images, Integer[] positions, int bestArea, int row_len) throws IOException {
    // Create a buffered image without transparency
    BufferedImage b_image = new BufferedImage(bestArea, bestArea, BufferedImage.TYPE_INT_RGB);
    Graphics2D g2d = b_image.createGraphics();
    g2d.setColor(Color.LIGHT_GRAY);
    g2d.fillRect(0, 0, b_image.getWidth(), b_image.getHeight());

    g2d.setColor(Color.BLACK);
    int hmax = SPACE_D;
    for (int i = 0; i < row_len; i++) {
      int row_w = SPACE_D;
      int row_h = 0;
      for (int j = 0; j < row_len; j++) {
        Integer p = positions[i * row_len + j];
        if (p != null) {
          Image image = images.get(p);
          g2d.drawImage(image, row_w, hmax, null);
          g2d.drawString("Img " + p, row_w + 5, hmax + image.getHeight(null) + 15);
          int w = image.getWidth(null) + SPACE_D;
          int h = image.getHeight(null) + SPACE_D;
          row_w += w;
          row_h = Math.max(row_h, h);
        }
      }
      hmax += row_h;
    }

    g2d.dispose();

    Files.createDirectories(Paths.get("imgs"));
    ImageIO.write(
        b_image, "PNG", new File("imgs/test-image-" + System.currentTimeMillis() + ".png"));
  }
}
