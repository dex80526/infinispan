/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009 Red Hat Inc. and/or its affiliates and other
 * contributors as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a full listing of
 * individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.infinispan.lucene;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.util.Set;

import org.infinispan.marshall.AbstractExternalizer;
import org.infinispan.util.Util;

/**
 * Used as a key for file headers in a cache
 * 
 * @since 4.0
 * @author Lukasz Moren
 * @author Sanne Grinovero
 */
public final class FileCacheKey implements Serializable {

   /** The serialVersionUID */
   private static final long serialVersionUID = -228474937509042691L;
   
   private final String indexName;
   private final String fileName;
   private final int hashCode;

   public FileCacheKey(final String indexName, final String fileName) {
      if (fileName == null)
         throw new IllegalArgumentException("filename must not be null");
      this.indexName = indexName;
      this.fileName = fileName;
      this.hashCode = generatedHashCode();
   }

   /**
    * Get the indexName.
    * 
    * @return the indexName.
    */
   public String getIndexName() {
      return indexName;
   }

   /**
    * Get the fileName.
    * 
    * @return the fileName.
    */
   public String getFileName() {
      return fileName;
   }

   @Override
   public int hashCode() {
      return hashCode;
   }
   
   private int generatedHashCode() {
      final int prime = 31;
      int result = prime + fileName.hashCode();
      return prime * result + indexName.hashCode();
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj)
         return true;
      if (obj == null)
         return false;
      if (FileCacheKey.class != obj.getClass())
         return false;
      FileCacheKey other = (FileCacheKey) obj;
      if (!fileName.equals(other.fileName))
         return false;
      return indexName.equals(other.indexName);
   }
   
   /**
    * Changing the encoding could break backwards compatibility
    * @see LuceneKey2StringMapper#getKeyMapping(String)
    */
   @Override
   public String toString() {
      return fileName + "|M|"+ indexName;
   }
   
   public static final class Externalizer extends AbstractExternalizer<FileCacheKey> {

      @Override
      public void writeObject(final ObjectOutput output, final FileCacheKey key) throws IOException {
         output.writeUTF(key.indexName);
         output.writeUTF(key.fileName);
      }

      @Override
      public FileCacheKey readObject(final ObjectInput input) throws IOException {
         String indexName = input.readUTF();
         String fileName = input.readUTF();
         return new FileCacheKey(indexName, fileName);
      }

      @Override
      public Integer getId() {
         return ExternalizerIds.FILE_CACHE_KEY;
      }

      @Override
      public Set<Class<? extends FileCacheKey>> getTypeClasses() {
         return Util.<Class<? extends FileCacheKey>>asSet(FileCacheKey.class);
      }
   }

}
