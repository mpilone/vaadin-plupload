
package org.mpilone.vaadin;

import java.io.IOException;
import java.io.OutputStream;

/**
 * An output stream that ignores close calls.
 */
class UncloseableOutputStream extends OutputStream {
  private final OutputStream delegate;

  /**
   * Constructs the stream.
   *
   * @param delegate the delegate stream to write to
   */
  public UncloseableOutputStream(OutputStream delegate) {
    this.delegate = delegate;
  }

  @Override
  public void write(byte[] b) throws IOException {
    delegate.write(b);
  }

  @Override
  public void write(int b) throws IOException {
    delegate.write(b);
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    delegate.write(b, off, len);
  }

  @Override
  public void close() throws IOException {
    delegate.flush();
  }

}
