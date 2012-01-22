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

import de.matthiasmann.javafreetype.FreeTypeFont;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Matthias Mann
 */
public final class FontData {

    public static final int NAME_FONT_FAMILY     = 1;
    public static final int NAME_FONT_SUBFAMILY  = 2;
    public static final int NAME_UNIQUE_ID       = 3;
    public static final int NAME_FULL_NAME       = 4;
    public static final int NAME_VERSION         = 5;
    public static final int NAME_POSTSCRIPT_NAME = 6;
    
    private final URL fontFile;
    private final FreeTypeFont font;
    private final IntMap<IntMap<Integer>> kerning;
    private final String[] names;
    private final ByteBuffer nameSection;
    private final ByteBuffer kernSection;

    final HashMap<Integer, TTFFontRenderer> fontRenderers;
    
    public FontData(URL url) throws IOException {
        InputStream is = url.openStream();
        ByteBuffer bb;
        try {
            bb = FreeTypeFont.toByteBuffer(is);
        } finally {
            is.close();
        }
        
        this.fontFile = url;
        this.font = FreeTypeFont.create(bb);
        this.kerning = new IntMap<IntMap<Integer>>();
        this.fontRenderers = new HashMap<Integer, TTFFontRenderer>();
        
        bb.order(ByteOrder.BIG_ENDIAN);
        
        nameSection = readSection(bb, "name");
        kernSection = readSectionOptional(bb, "kern");

        names = readNAME(nameSection);

        if(kernSection != null) {
            readKERN(kernSection);
        }
    }
    
    public static String[] getFontName(URL url) throws IOException {
        InputStream is = url.openStream();
        ByteBuffer bb;
        try {
            bb = FreeTypeFont.toByteBuffer(is);
        } finally {
            is.close();
        }
        
        bb.order(ByteOrder.BIG_ENDIAN);
        return readNAME(readSection(bb, "name"));
    }
    
    public String getName(int idx) {
        return names[idx];
    }

    public FreeTypeFont getFont() {
        return font;
    }

    public URL getFontFile() {
        return fontFile;
    }
    
    public boolean hasRawKerning() {
        return kernSection != null;
    }
    
    public TTFFontRenderer getFontRenderer(int fontSize) {
        return fontRenderers.get(fontSize);
    }

    public TTFFontRenderer setFontRenderer(int fontSize, TTFFontRenderer value) {
        return fontRenderers.put(fontSize, value);
    }

    public void destroy() {
        try {
            font.close();
        } catch (IOException ex) {
            Logger.getLogger(FontData.class.getName()).log(Level.SEVERE, "Could not close FreeTypeFont", ex);
        }
    }

    void readRawKerning(TTFFontRenderer fr, TTFFontRenderer.Glyph glyph) throws IOException {
        int version = kernSection.getChar(0);
        int nTables = kernSection.getChar(2);
        //System.out.println("version="+version+" nTables="+nTables);

        int tableOffset = 4;
        for(int table=0 ; table<nTables ; table++) {
            int tableLength = kernSection.getInt(tableOffset);
            int coverage = kernSection.getChar(tableOffset + 4);
            
            if ((coverage & 3) == 1) {  // only horizontal
                int format = coverage >> 8;
                switch(format) {
                    case 0: {
                        int numPairs = kernSection.getChar(tableOffset + 6);
                        int offset = tableOffset + 14;

                        for(int pair=0 ; pair<numPairs ; pair++,offset+=6) {
                            int from = kernSection.getChar(offset);
                            if(from == glyph.glyphIndex) {
                                int to = kernSection.getChar(offset + 2);
                                fr.setRawKerning(glyph, to);
                            }
                        }
                        break;
                    }
                    default:
                        Logger.getLogger(FontData.class.getName()).log(Level.WARNING,
                                "Unsupported kerning subtable format: {0} (kern table version: {1})",
                                new Object[]{format, version});
                }
            }

            tableOffset += tableLength;
        }
    }
    
    private void readKERN(ByteBuffer kernSection) {
        int version = kernSection.getChar(0);
        int nTables = kernSection.getChar(2);
        //System.out.println("version="+version+" nTables="+nTables);

        int tableOffset = 4;
        for(int table=0 ; table<nTables ; table++) {
            int tableLength = kernSection.getInt(tableOffset);
            int coverage = kernSection.getChar(tableOffset + 4);
            
            if ((coverage & 3) == 1) {  // only horizontal
                int format = coverage >> 8;
                switch(format) {
                    case 0: {
                        int numPairs = kernSection.getChar(tableOffset + 6);
                        int offset = tableOffset + 14;

                        for(int pair=0 ; pair<numPairs ; pair++,offset+=6) {
                            int from = kernSection.getChar(offset);
                            int to   = kernSection.getChar(offset + 2);
                            int kpx  = kernSection.getChar(offset + 4);
                            if (kpx != 0) {
                                addKerning(from, to, kpx);
                            }
                        }
                        break;
                    }
                    default:
                        Logger.getLogger(FontData.class.getName()).log(Level.WARNING,
                                "Unsupported kerning subtable format: {0} (kern table version: {1})",
                                new Object[]{format, version});
                }
            }

            tableOffset += tableLength;
        }
    }

    private void addKerning(int fromGlyph, int toGlyph, int kpx) {
        IntMap<Integer> adjTab = kerning.get(fromGlyph);
        if (adjTab == null) {
            adjTab = new IntMap<Integer>();
            kerning.put(fromGlyph, adjTab);
        }
        adjTab.put(toGlyph, kpx);
    }

    private static ByteBuffer readSectionOptional(ByteBuffer bb, String sectionName) throws IOException {
        assert sectionName.length() == 4;
        
        int ntabs = bb.getChar(4);
        for(int idx=0 ; idx<ntabs ; idx++) {
            int off = 12 + idx*16;
            
            boolean match = true;
            for(int i=0 ; i<4 ; i++) {
                if(bb.get(off + i) != sectionName.charAt(i)) {
                    match = false;
                    break;
                }
            }

            if(match) {
                int offset = bb.getInt(off + 8);
                int length = bb.getInt(off + 12);

                bb.clear();
                bb.position(offset);
                bb.limit(offset + length);
                return bb.slice().order(ByteOrder.BIG_ENDIAN);
            }
        }

        return null;
    }

    private static ByteBuffer readSection(ByteBuffer bb, String sectionName) throws IOException {
        ByteBuffer section = readSectionOptional(bb, sectionName);
        if(section == null) {
            throw new IOException("Missing '"+sectionName+"' section");
        }
        return section;
    }

    private static String[] readNAME(ByteBuffer nameSection) {
        int numStrings = nameSection.getChar(2);
        int strOffset = nameSection.getChar(4);

        String[] result = new String[7];
        int hasPreferred = 0;

        for(int i=0 ; i<numStrings ; i++) {
            int nameID = nameSection.getChar(i*12 + 12);
            
            if(nameID == 0 || nameID >= result.length) {
                continue;
            }
            if((hasPreferred & (1<<nameID)) != 0) {
                continue;
            }
            
            int platformID = nameSection.getChar(i*12 + 6);
            int encodingID = nameSection.getChar(i*12 + 8);
            int length = nameSection.getChar(i*12 + 14);
            int offset = nameSection.getChar(i*12 + 16);
            
            String str;
            if((platformID == 0 && encodingID == 3) || (platformID == 3 && encodingID == 1) ||
                    (platformID == 0 && encodingID == 0) || (platformID == 3 && encodingID == 0)) {
                str = readString(nameSection, strOffset + offset, length, "UTF-16BE");
            } else if(platformID == 1 && encodingID == 0) {
                str = readString(nameSection, strOffset + offset, length, "MacRoman");
            } else if((platformID == 1 && encodingID == 1) || (platformID == 3 && encodingID == 2)) {
                str = readString(nameSection, strOffset + offset, length, "JISAutoDetect");
            } else {
                System.out.println("Unknown platformID="+platformID+" encodingID="+encodingID);
                continue;
            }
            
            result[nameID] = str;

            int languageID = nameSection.getChar(i*12 + 10);
            boolean isPreferred =
                    (platformID == 3 && (languageID & 255) == 0x09) ||
                    (platformID == 1 && (languageID == 1));

            if(isPreferred) {
                hasPreferred  |= 1 << nameID;
            }
        }
        
        return result;
    }
    
    private static String readString(ByteBuffer bb, int off, int len, String encoding) {
        try {
            if (len > 0) {
                byte[] a = new byte[len];
                bb.position(off);
                bb.get(a);
                return new String(a, encoding);
            }
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(FontData.class.getName()).log(Level.SEVERE, "Can't decode string", ex);
        }
        return "";
    }
}
