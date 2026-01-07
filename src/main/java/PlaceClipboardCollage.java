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
import javax.imageio.ImageIO;

public class PlaceClipboardCollage {
  private record Permutation(Integer[] positions, int index, double compareValue)
      implements Comparable<Permutation> {
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
      // Min-heap based on compareValue
      // The goal is to reduce the total number of permutations to be checked. Therefore, we want to
      // prioritize those with a larger compareValue (larger area used) in order to check them
      // earlier and remove them faster.
      return Double.compare(o.compareValue, this.compareValue);
    }

    private Permutation swapAndCreate(int j, double compareValue) {
      Integer[] newPositions = positions.clone();
      Integer temp = newPositions[index];
      newPositions[index] = newPositions[j];
      newPositions[j] = temp;
      return new Permutation(newPositions, index + 1, compareValue);
    }

    private Permutation skipAndCreate(double compareValue) {
      return new Permutation(positions, positions.length, compareValue);
    }
  }

  private static final int SPACE_D = 25;
  private static final int POLLS_SOFT_LIMIT = 500_000;

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

    int bestArea = calculateArea(images, positions, row_len, positions.length) + 1;
    Integer[] bestPos = null;

    PriorityQueue<Permutation> queue = new PriorityQueue<>();
    HashSet<Permutation> visited = new HashSet<>();
    queue.add(new Permutation(positions, 1, 1));
    int polls = 0;
    int lastBestAreaPoll = 0;
    while (!queue.isEmpty()) {
      if (polls - lastBestAreaPoll >= POLLS_SOFT_LIMIT && bestPos != null) {
        // Stop if no better area found in a while
        break;
      }
      Permutation permutation = queue.poll();
      polls++;
      if (visited.contains(permutation)) {
        continue;
      }
      visited.add(permutation);
      int area = calculateArea(images, permutation.positions(), row_len, permutation.index());
      if (area >= bestArea) {
        continue;
      }
      if (permutation.index() == permutation.positions().length) {
        System.out.println("Found smaller area: " + area + " (polls: " + polls + ")");
        bestArea = area;
        bestPos = permutation.positions();
        lastBestAreaPoll = polls;
        continue;
      }
      for (int i = permutation.index(); i < permutation.positions().length; i++) {
        Permutation swapped = permutation.swapAndCreate(i, area);
        if (!visited.contains(swapped)) {
          queue.add(swapped);
        }
      }
    }
    System.out.println("Total permutations checked: " + polls);
    if (bestPos == null) {
      throw new IllegalStateException("No best position found");
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
