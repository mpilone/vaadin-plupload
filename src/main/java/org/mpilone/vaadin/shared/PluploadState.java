package org.mpilone.vaadin.shared;

import java.util.List;

import org.mpilone.vaadin.Plupload;

import com.vaadin.shared.ui.JavaScriptComponentState;

/**
 * Shared state for the {@link Plupload} component.
 * 
 * @author mpilone
 */
@SuppressWarnings("serial")
public class PluploadState extends JavaScriptComponentState {

  /**
   * The flag which indicates if the uploader has started uploading the file
   * queue.
   */
  public boolean started = false;

  /**
   * The flag which indicates if the browse button is enabled or not.
   */
  public boolean browseEnabled = true;

  /**
   * The unique ID for this instance of the uploader. This is used to create
   * unique elements in the DOM to support multiple upload components on a
   * single page.
   */
  public long instanceId;

  /**
   * Page URL to where the files will be uploaded to.
   */
  public String url;

  /**
   * This is a comma separated list of runtimes that you want to initialize the
   * uploader instance with. It will try to initialize each runtime in order if
   * one fails it will move on to the next one.
   */
  public String runtimes;

  /**
   * Maximum file size that the user can pick. This string can be in the
   * following formats 100b, 10kb, 10mb.
   */
  public long maxFileSize;

  /**
   * Enables you to chunk the file into smaller pieces for example if your PHP
   * backend has a max post size of 1MB you can chunk a 10MB file into 10
   * requests. To disable chunking, remove this config option from your setup.
   */
  public Long chunkSize;

  /**
   * Generate unique filenames when uploading. This will generate unique
   * filenames for the files so that they don't for example collide with
   * existing ones on the server.
   */
  public boolean uniqueNames;

  /**
   * The IDs of files that have been removed from the upload queue on the server
   * side.
   */
  public List<String> removedFileIds;

  /**
   * The flag which enables or disables multiple file selection when the user is
   * browsing for files. Note that event if multiple file selection is disabled,
   * the user may still add multiple files to the queue by selecting one after
   * another.
   */
  public boolean multiSelection;
}
