package ar.edu.itba.io;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class GridLightsFileRotator {

  private static final int AVOIDABLE_LINES = 1;

  public static void rotate(final String oldFilename, final String newFilename) throws IOException {
    final FileWriter fw = new FileWriter(newFilename);
    final BufferedReader br = new BufferedReader(new FileReader(oldFilename));

    String line = br.readLine();
    fw.write(line + "\n");
    final String[] dimensions = line.split("x");
    final int cols = Integer.valueOf(dimensions[1]);
    line = br.readLine();

    while (line != null) {
      final String[] oldPositions = line.split("-");
      fw.write(String.valueOf(rotate(Integer.valueOf(oldPositions[0]), cols)) + '-' +
              String.valueOf(rotate(Integer.valueOf(oldPositions[1]), cols)) + "\n");
      for (int i = 0; i < AVOIDABLE_LINES; i++) {
        fw.write(br.readLine() + "\n");
      }
      line = br.readLine();
    }

    end(fw, br);

  }

  private static int rotate(final int oldNumber, final int cols) {
    final int[] newPosition = new int[]{oldNumber % cols, cols - 1 - (oldNumber / cols)};
    return newPosition[0] * cols + newPosition[1];
  }

  private static void end(final FileWriter fw, final BufferedReader br) throws IOException {
    fw.close();
    br.close();
  }

}
