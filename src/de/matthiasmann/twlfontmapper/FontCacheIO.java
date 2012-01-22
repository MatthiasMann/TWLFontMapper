/*
 * Copyright (c) 2008-2012, Matthias Mann
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright notice,
 *       this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of Matthias Mann nor the names of its contributors may
 *       be used to endorse or promote products derived from this software
 *       without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package de.matthiasmann.twlfontmapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages font property cache
 * 
 * @author Matthias Mann
 */
public class FontCacheIO {
    
    private final File file;
    private final Properties fontCache;

    /**
     * Creates a font cache with the given file name.
     * 
     * <p>NOTE: This does [b]NOT[/b] read the font cache - you need to call
     * {@link #read() } for that.</p>
     * 
     * @param file the property cache file
     * @throws NullPointerException when file is null
     */
    public FontCacheIO(File file) {
        if(file == null) {
            throw new NullPointerException("file");
        }
        this.file = file;
        this.fontCache = new Properties();
    }

    public Properties getFontCache() {
        return fontCache;
    }
    
    /**
     * Reads the font cache from the file set in the constructor.
     * 
     * The font cache is cleared before the an attempt is made to load it.
     * 
     * @throws IOException if an IO error occured
     */
    public void read() throws IOException {
        fontCache.clear();
        FileInputStream fis = new FileInputStream(file);
        try {
            fontCache.loadFromXML(fis);
        } finally {
            fis.close();
        }
    }
    
    /**
     * Writes the font cache to the file set in the constructor.
     * 
     * It uses {@link Properties#storeToXML(java.io.OutputStream, java.lang.String, java.lang.String) }
     * with {@code UTF-8} encoding.
     * 
     * @throws IOException 
     */
    public void write() throws IOException {
        FileOutputStream fos = new FileOutputStream(file);
        try {
            fontCache.storeToXML(fos, "TWL font cache", "UTF-8");
        } finally {
            fos.close();
        }
    }
    
    /**
     * Creates a callback for use with {@link TWLFontMapper} to write the font
     * cache to file after it has been changed.
     * 
     * @return the callback
     * @see TWLFontMapper#setFontCacheChangedCB(java.lang.Runnable) 
     */
    public Runnable createWriteCallabck() {
        return new Runnable() {
            public void run() {
                try {
                    write();
                } catch (IOException ex) {
                    Logger.getLogger(FontCacheIO.class.getName()).log(Level.SEVERE,
                            "Could not write font cache", ex);
                }
            }
        };
    }
}
