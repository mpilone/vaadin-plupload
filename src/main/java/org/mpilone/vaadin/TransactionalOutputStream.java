
package org.mpilone.vaadin;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * An output stream that buffers the data in memory and only writes to the
 * delegate output stream when committed. The stream can be reused after calling
 * commit or rollback which provides an efficient in-memory buffer.
 *
 * @author mpilone
 */
class TransactionalOutputStream extends OutputStream {

  private final ByteBuffer buffer;
  private final OutputStream delegate;

  /**
   * Constructs the output stream which will buffer incoming data up to the
   * given capacity.
   *
   * @param capacity the maximum capacity in bytes
   * @param delegate the delegate stream to write to
   */
  public TransactionalOutputStream(int capacity, OutputStream delegate) {
    this.buffer = ByteBuffer.allocate(capacity);
    this.delegate = delegate;
  }

  @Override
  public void write(byte[] b) throws IOException {
    buffer.put(b);
  }

  @Override
  public void write(int b) throws IOException {
    buffer.put((byte) b);
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    buffer.put(b, off, len);
  }

  /**
   * Rolls back the written data.
   */
  public void rollback() {
    buffer.rewind();
  }

  /**
   * Returns the capacity that this stream was configured with.
   *
   * @return the in-memory storage capacity in bytes
   */
  public int getCapacity() {
    return buffer.capacity();
  }

  /**
   * Commits the written data by writing it to the delegate output stream.
   *
   * @throws IOException if a write to the delegate stream fails
   */
  public void commit() throws IOException {
    int available = buffer.position();
    buffer.rewind();

    byte[] buf = new byte[1024];
    while (available > 0) {
      int len = Math.min(buf.length, available);
      available -= len;

      buffer.get(buf, 0, len);
      delegate.write(buf, 0, len);
    }

    buffer.rewind();
  }
}
