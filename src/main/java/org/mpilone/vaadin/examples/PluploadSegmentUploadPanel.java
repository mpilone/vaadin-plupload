package org.mpilone.vaadin.examples;

import static java.lang.String.format;

import java.io.IOException;
import java.io.OutputStream;

import org.mpilone.vaadin.Plupload;
import org.mpilone.vaadin.Plupload.MultipleFileEvent;
import org.mpilone.vaadin.Plupload.SingleFileEvent;
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
    Plupload.UploadProgressListener, Plupload.FilesAddedListener,
    Plupload.FileUploadedListener, Plupload.UploadCompleteListener {

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

  /*
   * (non-Javadoc)
   * 
   * @see
   * org.prss.contentdepot.vaadin.Plupload.UploadProgressListener#uploadProgress
   * (org.prss.contentdepot.vaadin.Plupload.SingleFileEvent)
   */
  @Override
  public void uploadProgress(SingleFileEvent evt) {
    Integer percent = evt.getFile().getPercent();

    if (percent != null) {
      progressBar.setValue(percent / 100.0f);
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * org.prss.contentdepot.vaadin.Plupload.FilesAddedListener#filesAdded(org
   * .prss.contentdepot.vaadin.Plupload.MultipleFileEvent)
   */
  @Override
  public void filesAdded(MultipleFileEvent evt) {
    fileNameTxt.setValue(upload.getQueuedFiles().get(0).getName());
    submitBtn.setEnabled(true);
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * org.prss.contentdepot.vaadin.Plupload.FileUploadedListener#fileUploaded
   * (org.prss.contentdepot.vaadin.Plupload.SingleFileEvent)
   */
  @Override
  public void fileUploaded(SingleFileEvent evt) {

    log.info(format(
        "Upload complete with actual size [%d] and expected size [%d].",
        outstream.getCount(), evt.getFile().getSize()));

    // Generate the final response label. If we don't have an expected file size
    // (which can happen with the html4 runtime), we assume success.
    Label lbl = null;
    if (evt.getFile().getSize() == null
        || outstream.getCount() == evt.getFile().getSize()) {
      lbl = new Label("Success");
    }
    else {
      lbl = new Label("Failed");
    }

    progressBar.setEnabled(false);
    rootLayout.replaceComponent(progressBar, lbl);

    outstream.resetCount();
  }

  private ProgressIndicator progressBar;
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
    upload.setMultiSelection(false);
    upload.addUploadProgressListener(this);
    upload.addFilesAddedListener(this);
    upload.addFileUploadedListener(this);
    upload.setMaxQueuedFiles(1);
    upload.setReceiver(new Upload.Receiver() {
      @Override
      public OutputStream receiveUpload(String filename, String mimeType) {
        return outstream;
      }
    });
    rootLayout.addComponent(upload);

    // This will be swapped in when the upload starts.
    progressBar = new ProgressIndicator();
    progressBar.addStyleName("striped active");
    progressBar.setHeight("20px");
    progressBar.setEnabled(false);
    progressBar.setPollingInterval(1000);
  }

  protected void onSaveClicked() {
    submitBtn.setEnabled(false);
    progressBar.setEnabled(true);

    // Swap out the save button for the progress bar
    rootLayout.replaceComponent(submitBtn, progressBar);

    // Start the upload
    upload.setBrowseEnabled(false);
    upload.submitUpload();
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * org.prss.contentdepot.vaadin.Plupload.UploadCompleteListener#uploadComplete
   * (org.prss.contentdepot.vaadin.Plupload.MultipleFileEvent)
   */
  @Override
  public void uploadComplete(MultipleFileEvent evt) {
    log.info("All uploads complete.");
  }

}
