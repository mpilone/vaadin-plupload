package org.mpilone.vaadin.shared;

public class PluploadFile {
  private String id;
  private Integer loaded;
  private String name;
  private Integer percent;
  private Integer size;
  private Integer status;

  /**
   * @return the id
   */
  public String getId() {
    return id;
  }

  /**
   * @param id
   *          the id to set
   */
  public void setId(String id) {
    this.id = id;
  }

  /**
   * @return the loaded
   */
  public Integer getLoaded() {
    return loaded;
  }

  /**
   * @param loaded
   *          the loaded to set
   */
  public void setLoaded(Integer loaded) {
    this.loaded = loaded;
  }

  /**
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * @param name
   *          the name to set
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * @return the percent
   */
  public Integer getPercent() {
    return percent;
  }

  /**
   * @param percent
   *          the percent to set
   */
  public void setPercent(Integer percent) {
    this.percent = percent;
  }

  /**
   * @return the size
   */
  public Integer getSize() {
    return size;
  }

  /**
   * @param size
   *          the size to set
   */
  public void setSize(Integer size) {
    this.size = size;
  }

  /**
   * @return the status
   */
  public Integer getStatus() {
    return status;
  }

  /**
   * @param status
   *          the status to set
   */
  public void setStatus(Integer status) {
    this.status = status;
  }
}
