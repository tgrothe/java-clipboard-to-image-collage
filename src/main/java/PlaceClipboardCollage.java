import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import javax.imageio.ImageIO;

public class PlaceClipboardCollage {
  private static final int SPACE_D = 25;

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
    ArrayList<Integer> bestPos = null;
    ArrayDeque<ArrayList<Integer>> queue =
        new ArrayDeque<>(new Permutations().permute(positions).getAllPermutations());
    System.out.println("Total permutations to evaluate: " + queue.size());
    while (!queue.isEmpty()) {
      ArrayList<Integer> pos = queue.poll();
      int area = calculateArea(images, pos, row_len);
      if (area < bestArea) {
        bestArea = area;
        bestPos = pos;
      }
    }
    if (bestPos == null) {
      throw new IllegalStateException("No best position found");
    }

    saveImages(images, bestPos, bestArea, row_len);
  }

  private static int calculateArea(
      ArrayList<Image> images, ArrayList<Integer> positions, int row_len) {
    int wmax = SPACE_D;
    int hmax = SPACE_D;
    for (int i = 0; i < row_len; i++) {
      int row_w = SPACE_D;
      int row_h = 0;
      for (int j = 0; j < row_len; j++) {
        Integer p = positions.get(i * row_len + j);
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
      ArrayList<Image> images, ArrayList<Integer> positions, int bestArea, int row_len)
      throws IOException {
    // Create a buffered image without transparency
    BufferedImage b_image = new BufferedImage(bestArea, bestArea, BufferedImage.TYPE_INT_RGB);
    Graphics2D g2d = b_image.createGraphics();
    g2d.setColor(Color.LIGHT_GRAY);
    g2d.fillRect(0, 0, b_image.getWidth(), b_image.getHeight());

    int hmax = SPACE_D;
    for (int i = 0; i < row_len; i++) {
      int row_w = SPACE_D;
      int row_h = 0;
      for (int j = 0; j < row_len; j++) {
        Integer p = positions.get(i * row_len + j);
        if (p != null) {
          Image image = images.get(p);
          g2d.drawImage(image, row_w, hmax, null);
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
