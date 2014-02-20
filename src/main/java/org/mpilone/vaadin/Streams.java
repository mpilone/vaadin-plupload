
package org.mpilone.vaadin;

import java.io.Closeable;
import java.io.IOException;

/**
 * Utility methods for working with streams.
 *
 * @author mpilone
 */
 class Streams {
   /**
    * Attempts to commit the given output stream ignoring IO exceptions.
    *
    * @param outstream the stream to commit
    */
   static void tryCommit(TransactionalOutputStream outstream) {
     try {
       outstream.commit();
     }
     catch (IOException ex) {
       // Ignore
     }
   }

   /**
    * Attempts to close the given stream, ignoring IO exceptions.
    *
    * @param closeable the stream to be closed
    */
   static void tryClose(Closeable closeable) {
     try {
       closeable.close();
     }
     catch (IOException ex) {
       // Ignore
     }
   }
}
