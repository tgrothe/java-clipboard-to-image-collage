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
        try {
          Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
        } catch (IllegalStateException e) {
          System.out.println("Could not clear clipboard: " + e.getMessage());
          Thread.sleep(1000);
          continue;
        }
        sortAndSaveImages(images);
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

  private static int getRowWidth(ArrayList<Image> row) {
    int rowWidth = SPACE_D;
    for (Image img : row) {
      rowWidth += img.getWidth(null) + SPACE_D;
    }
    return rowWidth;
  }

  private static int getRowHeight(ArrayList<Image> row) {
    int rowHeight = 0;
    for (Image img : row) {
      rowHeight = Math.max(rowHeight, img.getHeight(null) + SPACE_D);
    }
    return rowHeight;
  }

  private static int calculateArea(ArrayList<ArrayList<Image>> imgTable) {
    int w_max = 0;
    int h_max = SPACE_D;
    for (ArrayList<Image> row : imgTable) {
      if (row.isEmpty()) {
        continue;
      }
      int row_w = getRowWidth(row);
      int row_h = getRowHeight(row);
      w_max = Math.max(w_max, row_w);
      h_max += row_h;
    }
    return Math.max(w_max, h_max);
  }

  private static void sortAndSaveImages(ArrayList<Image> images) throws IOException {
    ArrayList<Image> sortedImages = new ArrayList<>(images);
    // Sort images by height ascending
    sortedImages.sort(
        (img1, img2) -> {
          int h1 = img1.getHeight(null);
          int h2 = img2.getHeight(null);
          return Integer.compare(h1, h2);
        });

    ArrayList<ArrayList<Image>> imgTable = new ArrayList<>();
    final int row_len = images.size();
    for (int i = 0; i < row_len; i++) {
      imgTable.add(new ArrayList<>());
    }
    for (Image sortedImage : sortedImages) {
      imgTable.get(0).add(sortedImage);
    }

    ArrayList<ArrayList<Image>> bestTable = backtrack(imgTable, 0, 0);
    // Sort each row to have a consistent output
    for (ArrayList<Image> row : bestTable) {
      row.sort(
          (img1, img2) -> {
            int i1 = images.indexOf(img1);
            int i2 = images.indexOf(img2);
            return Integer.compare(i1, i2);
          });
    }

    Integer[] bestPositions = new Integer[row_len * row_len];
    for (int i = 0; i < bestTable.size(); i++) {
      ArrayList<Image> row = bestTable.get(i);
      for (int j = 0; j < row.size(); j++) {
        Image img = row.get(j);
        int index = images.indexOf(img);
        bestPositions[i * row_len + j] = index;
      }
    }
    int bestArea = calculateArea(bestTable);

    saveImages(images, bestPositions, bestArea, row_len);
  }

  private static ArrayList<ArrayList<Image>> shallowCopy(ArrayList<ArrayList<Image>> imgTable) {
    ArrayList<ArrayList<Image>> newTable = new ArrayList<>();
    for (ArrayList<Image> row : imgTable) {
      newTable.add(new ArrayList<>(row));
    }
    return newTable;
  }

  private static int max_depth = 0;

  private static ArrayList<ArrayList<Image>> backtrack(
      ArrayList<ArrayList<Image>> imgTable, int index, int depth) {
    if (depth > max_depth) {
      max_depth = depth;
      System.out.println("New max depth: " + max_depth);
    }
    // Limit depth to avoid excessive recursion
    if (depth >= 128) {
      return imgTable;
    }
    if (index + 1 >= imgTable.size()) {
      return imgTable;
    }
    if (imgTable.get(index).isEmpty()) {
      return imgTable;
    }

    // Calculate current area
    int bestArea = calculateArea(imgTable);
    ArrayList<ArrayList<Image>> bestTable = imgTable;

    // Try moving last image from row index to row index + 1 (e.g., balancing rows)
    ArrayList<ArrayList<Image>> copy = shallowCopy(imgTable);
    ArrayList<Image> r1 = copy.get(index);
    ArrayList<Image> r2 = copy.get(index + 1);
    r2.add(0, r1.remove(r1.size() - 1));

    ArrayList<ArrayList<Image>> bt = backtrack(copy, index + 1, depth + 1);
    int area = calculateArea(bt);
    if (area >= bestArea) {
      // No improvement, return best found so far
      return bestTable;
    }
    bestArea = area;
    bestTable = bt;

    // Can we do better by moving more images from row index to row index + 1?
    bt = backtrack(copy, index, depth + 1);
    area = calculateArea(bt);
    if (area < bestArea) {
      // Found a better arrangement
      bestTable = bt;
    }

    return bestTable;
  }

  private static void saveImages(
      ArrayList<Image> images, Integer[] positions, int bestArea, int row_len) throws IOException {
    System.out.println("Saving image with area: " + bestArea + "x" + bestArea + " pixels.");
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
