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
import java.util.HashMap;
import java.util.TreeSet;
import javax.imageio.ImageIO;

public class PlaceClipboardCollage {
  private record ImagesWrapper(ArrayList<ArrayList<Image>> table) {
    private static final ArrayList<Image> images = new ArrayList<>();
    private static final HashMap<Image, Integer> imageIndexMap = new HashMap<>();
    private static final TreeSet<ArrayList<ArrayList<Image>>> visited =
        new TreeSet<>(
            (t1, t2) -> {
              if (t1.size() != t2.size()) {
                return Integer.compare(t1.size(), t2.size());
              }
              for (int i = 0; i < t1.size(); i++) {
                if (t1.get(i).size() != t2.get(i).size()) {
                  return Integer.compare(t1.get(i).size(), t2.get(i).size());
                }
                for (int j = 0; j < t1.get(i).size(); j++) {
                  Image img1 = t1.get(i).get(j);
                  Image img2 = t2.get(i).get(j);
                  int idx1 = imageIndexMap.get(img1);
                  int idx2 = imageIndexMap.get(img2);
                  if (idx1 != idx2) {
                    return Integer.compare(idx1, idx2);
                  }
                }
              }
              return 0;
            });

    private static ImagesWrapper initWrapper(ArrayList<Image> images) {
      ImagesWrapper.images.clear();
      ImagesWrapper.images.addAll(images);

      ImagesWrapper.imageIndexMap.clear();
      for (int i = 0; i < images.size(); i++) {
        ImagesWrapper.imageIndexMap.put(images.get(i), i);
      }

      visited.clear();

      ArrayList<ArrayList<Image>> table = new ArrayList<>();
      for (int i = 0; i < images.size(); i++) {
        table.add(new ArrayList<>());
      }
      for (Image image : images) {
        table.get(0).add(image);
      }
      sortTableByHeight(table);

      return new ImagesWrapper(table);
    }

    private ImagesWrapper moveImage(int fromRow, int toRow) {
      ArrayList<ArrayList<Image>> newTable = new ArrayList<>();
      for (ArrayList<Image> row : table) {
        newTable.add(new ArrayList<>(row));
      }
      ArrayList<Image> srcRow = newTable.get(fromRow);
      ArrayList<Image> destRow = newTable.get(toRow);
      if (srcRow.isEmpty()) {
        throw new IllegalStateException("Source row is empty, cannot move image.");
      }
      destRow.add(0, srcRow.remove(srcRow.size() - 1));
      // sortRow(destRow);
      if (ImagesWrapper.isVisited(newTable)) {
        throw new IllegalStateException("Arrangement already visited.");
      }
      return new ImagesWrapper(newTable);
    }

    private static void sortRowByHeight(ArrayList<Image> row) {
      row.sort(
          (img1, img2) -> {
            int h1 = img1.getHeight(null);
            int h2 = img2.getHeight(null);
            return Integer.compare(h2, h1);
          });
    }

    private static void sortRowByIndex(ArrayList<Image> row) {
      row.sort(
          (img1, img2) -> {
            int i1 = imageIndexMap.get(img1);
            int i2 = imageIndexMap.get(img2);
            return Integer.compare(i1, i2);
          });
    }

    private static boolean isVisited(ArrayList<ArrayList<Image>> table) {
      if (visited.contains(table)) {
        return true;
      }
      visited.add(table);
      return false;
    }

    private static int getImagesSize() {
      return images.size();
    }

    private static void sortTableByHeight(ArrayList<ArrayList<Image>> table) {
      for (ArrayList<Image> row : table) {
        sortRowByHeight(row);
      }
    }

    private static void sortTableByIndex(ArrayList<ArrayList<Image>> table) {
      for (ArrayList<Image> row : table) {
        sortRowByIndex(row);
      }
    }

    private boolean isEmptyRow(int row) {
      return table.get(row).isEmpty();
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

    private int calculateArea() {
      int w_max = 0;
      int h_max = SPACE_D;
      for (ArrayList<Image> row : table) {
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
  }

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

  private static void sortAndSaveImages(ArrayList<Image> images) throws IOException {
    ImagesWrapper wrapper = ImagesWrapper.initWrapper(images);

    wrapper = backtrack(wrapper, 0, 0);

    saveImages(wrapper);
  }

  private static int max_depth = 0;

  private static ImagesWrapper backtrack(ImagesWrapper wrapper, int index, int depth) {
    // Debug info
    if (depth > max_depth) {
      max_depth = depth;
      System.out.println("New max depth: " + max_depth);
    }

    // Limit depth to avoid excessive recursion
    if (depth >= 128) {
      return wrapper;
    }
    if (index + 1 >= ImagesWrapper.getImagesSize()) {
      return wrapper;
    }
    if (wrapper.isEmptyRow(index)) {
      return wrapper;
    }

    // Calculate the current best area to beat...
    int bestArea = wrapper.calculateArea();
    ImagesWrapper bestWrapper = wrapper;

    // Don't do anything in this row...
    ImagesWrapper bt = backtrack(wrapper, index + 1, depth + 1);
    int area = bt.calculateArea();
    if (area < bestArea) {
      // Found a better arrangement
      bestArea = area;
      bestWrapper = bt;
    }

    // Move image into next row...
    ImagesWrapper newWrapper = wrapper.moveImage(index, index + 1);
    bt = backtrack(newWrapper, index, depth + 1);
    area = bt.calculateArea();
    if (area < bestArea) {
      // Found a better arrangement
      bestWrapper = bt;
    }

    // The winner is...
    return bestWrapper;
  }

  private static void saveImages(ImagesWrapper wrapper) throws IOException {
    int bestArea = wrapper.calculateArea();
    int row_len = ImagesWrapper.getImagesSize();
    ArrayList<ArrayList<Image>> table = wrapper.table();
    ImagesWrapper.sortTableByIndex(table);
    ArrayList<Image> images = ImagesWrapper.images;
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
        if (i < table.size() && j < table.get(i).size()) {
          int p = images.indexOf(table.get(i).get(j));
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
