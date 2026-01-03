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
import java.util.Comparator;
import java.util.function.Function;
import javax.imageio.ImageIO;

public class PlaceClipboardCollage {
  private static class GridImage {
    Image image;
    int x, y;
    int w, h;

    private GridImage(Image i, int x, int y, int w, int h) {
      image = i;
      this.x = x;
      this.y = y;
      this.w = w;
      this.h = h;
    }

    private int getAbsX() {
      double w2 = (w - image.getWidth(null) / (double) wid) / 2.0 * wid;
      return x * wid + (int) w2;
    }

    private int getAbsY() {
      double h2 = (h - image.getHeight(null) / (double) wid) / 2.0 * wid;
      return y * wid + (int) h2;
    }
  }

  private static final int wid = 50;
  private static ArrayList<GridImage> bestGridImages = null;

  @Deprecated
  private static final Comparator<Image> comparator0 =
      (o1, o2) -> {
        int w1 = (int) Math.ceil(o1.getWidth(null) / (double) wid);
        int w2 = (int) Math.ceil(o2.getWidth(null) / (double) wid);
        int h1 = (int) Math.ceil(o1.getHeight(null) / (double) wid);
        int h2 = (int) Math.ceil(o2.getHeight(null) / (double) wid);
        return w1 * h1 - w2 * h2;
      };

  private static final Comparator<ArrayList<GridImage>> comparator =
      (o1, o2) -> {
        int wmax1 = 0;
        int hmax1 = 0;
        int wmax2 = 0;
        int hmax2 = 0;
        for (GridImage gridImage : o1) {
          if (gridImage.x + gridImage.w > wmax1) {
            wmax1 = gridImage.x + gridImage.w;
          }
          if (gridImage.y + gridImage.h > hmax1) {
            hmax1 = gridImage.y + gridImage.h;
          }
        }
        for (GridImage gridImage : o2) {
          if (gridImage.x + gridImage.w > wmax2) {
            wmax2 = gridImage.x + gridImage.w;
          }
          if (gridImage.y + gridImage.h > hmax2) {
            hmax2 = gridImage.y + gridImage.h;
          }
        }
        return Math.max(wmax1, hmax1) - Math.max(wmax2, hmax2);
      };
  private static final Function<ArrayList<GridImage>, Integer> getMaxW =
      (l) -> l.stream().mapToInt(e -> e.x * wid + e.w * wid).max().orElseThrow();
  private static final Function<ArrayList<GridImage>, Integer> getMaxH =
      (l) -> l.stream().mapToInt(e -> e.y * wid + e.h * wid).max().orElseThrow();
  private static long steps = 0;

  public static void main(String[] args) throws Exception {
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
        saveImages(images);
        System.out.println("Ready.");
      }
      Thread.sleep(1000);
    }
  }

  private static Image getImageFromClipboard() throws Exception {
    Transferable transferable = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
    if (transferable != null && transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) {
      return (Image) transferable.getTransferData(DataFlavor.imageFlavor);
    } else {
      return null;
    }
  }

  private static boolean setGrid(boolean[][] grid, int x, int y, int w, int h, boolean val) {
    for (int i = 0; i < w; i++) {
      for (int j = 0; j < h; j++) {
        if (i + x >= grid.length || j + y >= grid.length || grid[i + x][j + y] == val) {
          return false;
        }
      }
    }
    for (int i = 0; i < w; i++) {
      for (int j = 0; j < h; j++) {
        grid[i + x][j + y] = val;
      }
    }
    return true;
  }

  private static void backtrackingAll(
      ArrayList<Image> images, ArrayList<GridImage> gridImages, boolean[][] grid, int i) {
    steps++;
    if (steps >= 10_000_000) {
      return;
    }
    if (i == images.size()) {
      if (bestGridImages == null || comparator.compare(gridImages, bestGridImages) < 0) {
        bestGridImages = new ArrayList<>(gridImages);
      }
      return;
    }
    if (bestGridImages != null && comparator.compare(gridImages, bestGridImages) >= 0) {
      return;
    }
    Image image = images.get(i);
    int w = (int) Math.ceil(image.getWidth(null) / (double) wid);
    int h = (int) Math.ceil(image.getHeight(null) / (double) wid);
    for (int x = 0; x < grid.length; x++) {
      for (int y = 0; y < grid.length; y++) {
        if (setGrid(grid, x, y, w, h, true)) {
          GridImage gi = new GridImage(image, x, y, w, h);
          gridImages.add(gi);
          backtrackingAll(images, gridImages, grid, i + 1);
          gridImages.remove(gridImages.size() - 1);
          setGrid(grid, x, y, w, h, false);
        }
      }
    }
  }

  private static void saveImages(ArrayList<Image> images) throws IOException {
    ArrayList<Image> images2 = new ArrayList<>(images);
    steps = 0;
    backtrackingAll(images2, new ArrayList<>(), new boolean[100][100], 0);

    // Create a buffered image without transparency
    BufferedImage bimage =
        new BufferedImage(
            getMaxW.apply(bestGridImages),
            getMaxH.apply(bestGridImages),
            BufferedImage.TYPE_INT_RGB);
    Graphics2D g2d = bimage.createGraphics();
    g2d.setColor(Color.LIGHT_GRAY);
    g2d.fillRect(0, 0, bimage.getWidth(), bimage.getHeight());
    for (GridImage gridImage : bestGridImages) {
      g2d.drawImage(gridImage.image, gridImage.getAbsX(), gridImage.getAbsY(), null);
    }
    g2d.dispose();

    images.clear();
    for (GridImage gridImage : bestGridImages) {
      images.add(gridImage.image);
    }
    bestGridImages = null;

    Files.createDirectories(Paths.get("imgs"));
    ImageIO.write(
        bimage, "PNG", new File("imgs/test-image-" + System.currentTimeMillis() + ".png"));
  }
}
