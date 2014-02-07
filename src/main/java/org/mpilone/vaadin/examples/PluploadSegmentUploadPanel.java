package org.mpilone.vaadin.examples;

import static java.lang.String.format;

import java.io.IOException;
import java.io.OutputStream;

import org.mpilone.vaadin.Plupload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.ui.*;
import com.vaadin.ui.Button.ClickEvent;

/**
 * Example of how to use the {@link Plupload} component.
 *
 * @author mpilone
 */
public class PluploadSegmentUploadPanel extends CustomComponent implements
    Upload.ProgressListener,
    Plupload.FinishedListener {

  /**
   * An output stream that simple throws away all bytes similar to /dev/null.
   *
   * @author mpilone
   */
  private static class NullOutputStream extends OutputStream {
    /*
     * (non-Javadoc)
     *
     * @see java.io.OutputStream#write(int)
     */
    @Override
    public void write(int b) throws IOException {
      // Send to the great bit bucket in the sky.
    }
  }

  /**
   * An output stream wrapper that counts the number of bytes written.
   *
   * @author mpilone
   */
  private static class CountingOutputStream extends OutputStream {
    private OutputStream delegate;
    private long count;

    public CountingOutputStream(OutputStream delegate) {
      this.delegate = delegate;
    }

    /*
     * (non-Javadoc)
     *
     * @see java.io.OutputStream#write(int)
     */
    @Override
    public void write(int b) throws IOException {
      count++;
      delegate.write(b);
    }

    public void resetCount() {
      count = 0;
    }

    public long getCount() {
      return count;
    }
  }

  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = 1L;

  /**
   * Log for this class.
   */
  private final Logger log = LoggerFactory.getLogger(getClass());

  @Override
  public void updateProgress(long readBytes, long contentLength) {

    float percent = (float) readBytes / (float) contentLength;

      progressBar.setValue(percent);
  }

  @Override
  public void uploadFinished(Plupload.FinishedEvent evt) {

    log.info(format(
        "Upload complete with actual size [%d] and expected size [%d].",
        outstream.getCount(), evt.getLength()));

    // Generate the final response label. If we don't have an expected file size
    // (which can happen with the html4 runtime), we assume success.
    Label lbl;
    if (outstream.getCount() == evt.getLength()) {
      lbl = new Label("Success");
    }
    else {
      lbl = new Label("Failed");
    }

    progressBar.setEnabled(false);
    rootLayout.replaceComponent(progressBar, lbl);

    outstream.resetCount();
  }

  private ProgressBar progressBar;
  private TextField fileNameTxt;
  private Plupload upload;
  private Button submitBtn;
  private VerticalLayout rootLayout;
  private CountingOutputStream outstream;

  @SuppressWarnings("serial")
  public PluploadSegmentUploadPanel() {

    outstream = new CountingOutputStream(new NullOutputStream());

    rootLayout = new VerticalLayout();
    rootLayout.setMargin(true);
    rootLayout.setSpacing(true);
    setCompositionRoot(rootLayout);

    Label lbl = new Label("Plupload Demo");
    rootLayout.addComponent(lbl);

    submitBtn = new Button("Save", new Button.ClickListener() {
      @Override
      public void buttonClick(ClickEvent event) {
        onSaveClicked();
      }
    });
    submitBtn.setEnabled(false);
    rootLayout.addComponent(submitBtn);

    lbl = new Label("File");
    lbl.setSizeUndefined();
    rootLayout.addComponent(lbl);

    fileNameTxt = new TextField();
    fileNameTxt.setEnabled(false);
    rootLayout.addComponent(fileNameTxt);

    upload = new Plupload();
    upload.setChunkSize(5 * 1024 * 1024L);
    upload.setMaxFileSize(500 * 1024 * 1024L);
    //upload.setMultiSelection(false);
    upload.addProgressListener(this);
    upload.addFinishedListener(this);
//    upload.setMaxQueuedFiles(1);
    upload.setReceiver(new Upload.Receiver() {
      @Override
      public OutputStream receiveUpload(String filename, String mimeType) {
        return outstream;
      }
    });
    rootLayout.addComponent(upload);

    // This will be swapped in when the upload starts.
    progressBar = new ProgressBar();
    progressBar.addStyleName("striped active");
    progressBar.setHeight("20px");
    progressBar.setEnabled(false);
//    progressBar.setPollingInterval(1000);
  }

  protected void onSaveClicked() {
    submitBtn.setEnabled(false);
    progressBar.setEnabled(true);

    // Swap out the save button for the progress bar
    rootLayout.replaceComponent(submitBtn, progressBar);

    // Start the upload
    //upload.setBrowseEnabled(false);
    upload.submitUpload();
  }


}
