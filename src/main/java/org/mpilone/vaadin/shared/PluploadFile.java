
package org.mpilone.vaadin.shared;

/**
 * File information defined by Plupload.
 *
 * @author mpilone
 */
public class PluploadFile {
  private String name;
  private long size;
  private String type;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public long getSize() {
    return size;
  }

  public void setSize(long size) {
    this.size = size;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

}
