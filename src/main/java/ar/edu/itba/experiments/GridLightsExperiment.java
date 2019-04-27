package ar.edu.itba.experiments;

import static java.lang.Math.abs;

import ar.edu.itba.model.EventsCounter;
import ar.edu.itba.model.CounterPane;
import ar.edu.itba.model.LightsGridPane;
import ar.edu.itba.model.StartScreen;
import ar.edu.itba.model.behaviours.*;
import ar.edu.itba.senders.StimulusSender;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.CornerRadii;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

public class GridLightsExperiment extends Application {

  private static final Random RANDOM = ThreadLocalRandom.current();
  private static final List<int[]> movements = Arrays.asList(new int[]{-1, 0}, new int[]{1, 0}, new int[]{0, 1},
          new int[]{0, -1});
  private static final List<MovementBehaviour> MOVEMENT_PATTERNS = Arrays.asList(new LeftRightBehaviour(), new UpDownBehaviour(),
          (p, r, c) -> new int[]{0, 0}, new ClockwiseBehaviour(), new CounterClockwiseBehaviour());
  private static final int MAX_ITERATION = 2;
  private static final int ROWS = 3;
  private static final int COLS = 3;
  private static final boolean SEND = false;
  private static final Duration STEP_DURATION = Duration.seconds(2);
  private static final String HOST = "10.17.2.185";
  private static final int PORT = 15361;

  private int iteration = 1;
  private int[] persecutorPosition;
  private int[] pursuedPosition;

  private static final String START = "start";
  private static final String FINISH = "finish";
  private static final String CLOSER = "closer";
  private static final String SAME_DISTANCE = "same distance";
  private static final String FURTHER = "further";
  private static final String[] LABELS = new String[]{START, FINISH, CLOSER, SAME_DISTANCE, FURTHER};
  private static final EventsCounter EVENTS_COUNTER = new EventsCounter(LABELS);

  private static final String FILENAME = "grid_lights_experiment";

  private BorderPane pane;
  private CounterPane counterPane;
  private LightsGridPane currentGrid;

  public static void main(String[] args) {
    launch(args);
  }

  @Override
  public void start(Stage primaryStage) {
    final StartScreen startScreen = new StartScreen();
    startScreen.setOnStart(() -> {
      pane.setCenter(counterPane);
      counterPane.startTimer();
    });

    counterPane = new CounterPane();
    counterPane.setOnTimerFinished(() -> {
      try {
        startExperiment();
      } catch (IOException e) {
        e.printStackTrace();
      }
    });

    pane = new BorderPane(startScreen);
    pane.setBackground(
            new Background(new BackgroundFill(Color.BLACK, CornerRadii.EMPTY, Insets.EMPTY)));

    primaryStage.setTitle("Square Lights");
    primaryStage.setScene(new Scene(pane));
    primaryStage.setResizable(false);
    primaryStage.setMaximized(true);
    setMaxSize(primaryStage);
    primaryStage.setFullScreen(true);
    primaryStage.show();
  }

  private static void setMaxSize(final Stage primaryStage) {
    final Screen screen = Screen.getPrimary();
    final Rectangle2D bounds = screen.getVisualBounds();

    primaryStage.setX(bounds.getMinX());
    primaryStage.setY(bounds.getMinY());
    primaryStage.setWidth(bounds.getWidth());
    primaryStage.setHeight(bounds.getHeight());
  }

  private void startExperiment() throws IOException {
    FileWriter fileWriter = new FileWriter(FILENAME + '_' + String.valueOf(iteration));
    fileWriter.write(String.valueOf(ROWS) + 'x' + String.valueOf(COLS));
    fileWriter.write('\n');
    EVENTS_COUNTER.count(START);

    persecutorPosition = newStartingPosition();
    pursuedPosition = newPursuedPosition();
    final MovementBehaviour movementBehaviour = MOVEMENT_PATTERNS.get(RANDOM.nextInt(MOVEMENT_PATTERNS.size()));
    currentGrid = new LightsGridPane(ROWS, COLS, persecutorPosition, pursuedPosition);
    pane.setCenter(currentGrid);
    fileWriter.write(stateToString());

    final StimulusSender sender = new StimulusSender();
    if (SEND) {
      try {
        sender.open(HOST, PORT);
        sender.send(3L, 0L);
      } catch (final IOException e) {
        e.printStackTrace();
        return;
      }
    }

    final Timeline timeline = new Timeline();
    final KeyFrame keyFrame = new KeyFrame(STEP_DURATION, e -> {
      final int prevDistance = distanceToPursued(pursuedPosition);
      final List<int[]> validMovements = movements.stream()
              .filter(currentGrid::isValidOffset)
              .collect(Collectors.toList());
      final int[] movement = validMovements.get(RANDOM.nextInt(validMovements.size()));
      moveLightWithOffset(persecutorPosition, movement, currentGrid::movePersecutorWithOffset);
      if (!Arrays.equals(persecutorPosition, pursuedPosition)) {
        moveLightWithOffset(pursuedPosition, movementBehaviour.getOffset(pursuedPosition, ROWS, COLS),
                currentGrid::movePursuedWithOffset);
      }

      try {
        fileWriter.write(stateToString());
      } catch (IOException e1) {
        e1.printStackTrace();
      }

      if (distanceToPursued(pursuedPosition) == 0) {
        EVENTS_COUNTER.count(FINISH, CLOSER);
        timeline.stop();
        try {
          fileWriter.close();
        } catch (IOException e1) {
          e1.printStackTrace();
        }
        if (SEND) {
          try {
            sender.send(4L, 0L);
            sender.close();
          } catch (Exception e1) {
            e1.printStackTrace();
          }
        }
        if (iteration < MAX_ITERATION) {
          iteration++;
          final Timeline tl = new Timeline(new KeyFrame(STEP_DURATION, oe -> {
            pane.setCenter(counterPane);
            counterPane.startTimer();
          }));
          tl.play();
        } else {
          System.out.println(EVENTS_COUNTER);
        }
      } else {
        final int currentDistance = distanceToPursued(pursuedPosition);
        final long distanceDifference = prevDistance - currentDistance;
        EVENTS_COUNTER.count(getLabelByDistanceDifference(distanceDifference));
        if (SEND) {
          try {
            sender.send(distanceDifference >= 0 ? 1 : 2, 0L);
          } catch (Exception exception) {
            exception.printStackTrace();
          }
        }
      }
    });
    timeline.getKeyFrames().add(keyFrame);
    timeline.setCycleCount(Timeline.INDEFINITE);
    timeline.play();
  }

  private void moveLightWithOffset(int[] position, final int[] offset, final Consumer<int[]> consumer) {
    position[0] += offset[0];
    position[1] += offset[1];
    consumer.accept(offset);
  }

  private int[] newStartingPosition() {
    return new int[]{RANDOM.nextInt(ROWS), RANDOM.nextInt(COLS)};
  }

  private int[] newPursuedPosition() {
    int[] pursuedPosition;

    do {
      pursuedPosition = new int[]{RANDOM.nextInt(ROWS), RANDOM.nextInt(COLS)};
    } while (distanceToPursued(pursuedPosition) <= 1);

    return pursuedPosition;
  }

  private int distanceToPursued(final int[] goalPosition) {
    return abs(persecutorPosition[0] - goalPosition[0]) + abs(persecutorPosition[1] - goalPosition[1]);
  }

  private String getLabelByDistanceDifference(final long distanceDifference) {
    if (distanceDifference == 0) {
      return SAME_DISTANCE;
    }
    return distanceDifference > 0 ? CLOSER : FURTHER;
  }

  private String stateToString() {
    StringBuilder sb = new StringBuilder();
    for (int row = 0; row < ROWS; row++) {
      for (int col = 0; col < COLS; col++) {
        if (pursuedPosition[0] == row && pursuedPosition[1] == col) {
          sb.append('G');
        } else if (persecutorPosition[0] == row && persecutorPosition[1] == col) {
          sb.append('P');
        } else {
          sb.append('-');
        }
      }
      sb.append('\n');
    }
    sb.append('\n');
    return sb.toString();
  }
}
