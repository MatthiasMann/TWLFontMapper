/*
 * Copyright (c) 2008-2011, Matthias Mann
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
import de.matthiasmann.twl.renderer.Font;
import de.matthiasmann.twl.renderer.FontMapper;
import de.matthiasmann.twl.renderer.FontParameter;
import de.matthiasmann.twl.renderer.lwjgl.LWJGLRenderer;
import de.matthiasmann.twl.utils.StateSelect;
import de.matthiasmann.twl.utils.StringList;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.lwjgl.LWJGLUtil;

/**
 *
 * @author Matthias Mann
 */
public class TWLFontMapper implements FontMapper {
    
    private static final int STYLE_MASK = STYLE_BOLD | STYLE_ITALIC;

    private final LWJGLRenderer renderer;
    private final HashMap<String, FontData> fontData;
    private final HashMap<String, Entry[]> families;
    private final HashMap<String, StringList> fontAlias;
    
    private Properties fontCache;
    private Runnable fontCacheChangedCB;
    private boolean fontCacheChanged;
    
    private TWLFontMapper(LWJGLRenderer renderer) {
        this.renderer = renderer;
        this.fontData = new HashMap<String, FontData>();
        this.families = new HashMap<String, Entry[]>();
        this.fontAlias = new HashMap<String, StringList>();
        
        assert STYLE_NORMAL == 0;
    }
    
    public static TWLFontMapper create(LWJGLRenderer renderer) {
        TWLFontMapper fontMapper = new TWLFontMapper(renderer);
        renderer.setFontMapper(fontMapper);
        return fontMapper;
    }

    public Properties getFontCache() {
        return fontCache;
    }

    public void setFontCache(Properties fontCache) {
        this.fontCache = fontCache;
        this.fontCacheChanged = false;
    }

    public Runnable getFontCacheChangedCB() {
        return fontCacheChangedCB;
    }

    public void setFontCacheChangedCB(Runnable fontCacheChangedCB) {
        this.fontCacheChangedCB = fontCacheChangedCB;
        this.fontCacheChanged = false;
    }
    
    public void addFontAlias(String from, String to) {
        if(from == null) {
            throw new NullPointerException("from");
        }
        if(to == null) {
            throw new NullPointerException("to");
        }
        
        from = from.toLowerCase(Locale.ENGLISH);
        to = to.toLowerCase(Locale.ENGLISH);
        
        StringList list = fontAlias.get(from);
        for(StringList l=list ; l!=null ; l=l.getNext()) {
            if(to.equals(l.getValue())) {
                return;
            }
        }
        fontAlias.put(from, new StringList(to, list));
    }
    
    @Override
    public Font getFont(StringList fontFamilies, int fontSize, int style,
            StateSelect select, FontParameter ... parameterList) {
        if(fontFamilies == null) {
            throw new NullPointerException("fontFamilies");
        }
        if(fontSize <= 0) {
            throw new IllegalArgumentException("fontSize");
        }
        if(select == null) {
            throw new NullPointerException("select");
        }
        if(parameterList == null) {
            throw new NullPointerException("parameterList");
        }
        if(select.getNumExpressions() + 1 != parameterList.length) {
            throw new IllegalArgumentException("select.getNumExpressions() + 1 != parameterList.length");
        }
        
        int urlIdx = style & STYLE_MASK;
        Entry fallback = null;
        Entry fontEntry = null;
        
        do {
            String family = fontFamilies.getValue().toLowerCase(Locale.ENGLISH);
            Entry[] entries = families.get(family);
            
            if(entries == null) {
                StringList aliasList = fontAlias.get(family);
                while(aliasList != null && entries == null) {
                    entries = families.get(aliasList.getValue());
                    aliasList = aliasList.getNext();
                }
            }
            
            if(entries != null) {
                fontEntry = entries[urlIdx];
                if(fontEntry != null) {
                    break;
                }
                if(fallback == null) {
                    fallback = entries[STYLE_NORMAL];
                }
            }
            
            fontFamilies = fontFamilies.getNext();
        } while(fontFamilies != null);
        
        if(fontEntry == null) {
            fontEntry = fallback;
        }
        if(fontEntry != null) {
            TTFFontRenderer fontRenderer = getFontRenderer(fontEntry.url, fontSize);
            if(fontRenderer != null) {
                return new FontImpl(fontRenderer, select, parameterList);
            }
        }
        
        return null;
    }
    
    private TTFFontRenderer getFontRenderer(URL url, int fontSize) {
        try {
            String fdKey = url.toString();

            FontData fd = fontData.get(fdKey);
            if(fd != null) {
                TTFFontRenderer fontRenderer = fd.getFontRenderer(fontSize);
                if(fontRenderer != null) {
                    return fontRenderer;
                }
            } else {
                //System.out.println("Loading font " + url);
                //long startTime = System.nanoTime();
                fd = new FontData(url);
                //System.out.println((System.nanoTime()-startTime) / 1000 + " us");
                fontData.put(fdKey, fd);
            }

            //System.out.println("Creating font size " + fontSize + " for " + url);
            FreeTypeFont font = fd.getFont();
            FreeTypeFont.Size size = font.createNewSize();
            font.setActiveSize(size);
            font.setPixelSize(0, fontSize);
            TTFFontRenderer fontRenderer = new TTFFontRenderer(renderer, fd, size);
            fd.setFontRenderer(fontSize, fontRenderer);
            
            return fontRenderer;
        } catch (IOException ex) {
            getLogger().log(Level.SEVERE, null, ex);
            return null;
        }
    }
    
    @Override
    public boolean registerFont(String fontFamily, int style, URL url) {
        if(fontFamily.indexOf(',') >= 0) {
            throw new IllegalArgumentException("fontFamily must not contain a ','");
        }
        if(url == null) {
            throw new NullPointerException("url");
        }
        
        boolean isWeak = (style & REGISTER_WEAK) == REGISTER_WEAK;
        
        style &= STYLE_MASK;
        
        fontFamily = fontFamily.toLowerCase(Locale.ENGLISH);
        Entry[] entries = families.get(fontFamily);
        
        if(entries == null) {
            entries = new Entry[STYLE_MASK+1];
            families.put(fontFamily, entries);
        }
        
        Entry entry = entries[style];
        if(entry == null) {
            entries[style] = new Entry(url, isWeak);
        } else {
            if(isWeak) {
                return false;
            }
            entry.url = url;
            entry.weak = false;
        }
        
        return true;
    }

    @Override
    public boolean registerFont(String fontFamily, URL url) throws IOException {
        if(fontFamily.indexOf(',') >= 0) {
            throw new IllegalArgumentException("fontFamily must not contain a ','");
        }
        
        try {
            if("file".equals(url.getProtocol())) {
                File file = new File(url.toURI());
                return registerFontFromFile(file, url, fontFamily);
            } else {
                return doRegisterFont(url, null, 0, 0, fontFamily);
            }
        } catch (URISyntaxException ex) {
            throw (IOException)(new IOException(ex.getMessage()).initCause(ex));
        } finally {
            checkFontCacheChanged();
        }
    }
    
    public void registerFonts(File folder, boolean recursive) {
        for(File file : folder.listFiles()) {
            if(file.isDirectory()) {
                if(recursive && !"..".equals(file.getName())) {
                    registerFonts(file, true);
                }
            } else {
                String name = file.getName().toLowerCase(Locale.ENGLISH);
                if(name.endsWith(".ttf") && file.canRead()) {
                    try {
                        registerFontFromFile(file, null, null);
                    } catch (IOException ex) {
                        getLogger().log(
                                Level.WARNING, "Unable to parse font: " + file, ex);
                    }
                }
            }
        }
        checkFontCacheChanged();
    }
    
    public void registerSystemFonts() {
        switch(LWJGLUtil.getPlatform()) {
            case LWJGLUtil.PLATFORM_LINUX:
                registerFonts(new File("/usr/share/fonts/truetype/"), true);
                registerFonts(new File("/usr/local/share/fonts/"), true);
                registerFonts(new File(new File(System.getProperty("user.home")), ".fonts"), true);
                break;
            case LWJGLUtil.PLATFORM_WINDOWS:
                registerFonts(new File(new File(System.getenv("SYSTEMROOT")), "Fonts"), true);
                break;
            default:
                getLogger().log(Level.WARNING, "Unsupported OS: {1}", LWJGLUtil.getPlatformName());
        }
    }
    
    private boolean registerFontFromFile(File file, URL url, String targetFontFamily) throws IOException {
        long lastModified = file.lastModified();
        long fileSize = file.length();
        String fileName = file.getPath();
        String cacheEntry = (fontCache != null) ? fontCache.getProperty(fileName) : null;

        try {
            if(url == null) {
                url = new File(fileName).toURI().toURL();
            }
            
            int cacheResult = registerFromCache(url, lastModified, fileSize, cacheEntry, targetFontFamily);
            if(cacheResult != NOT_IN_CACHE) {
                return cacheResult == SUCCEEDED;
            }
            
            return doRegisterFont(url, fileName, lastModified, fileSize, targetFontFamily);
        } catch (IOException ex) {
            if(fontCache != null) {
                fontCache.put(fileName, String.format("%d,%d", lastModified, fileSize));
                fontCacheChanged = true;
            }
            throw ex;
        }
    }
    
    private boolean doRegisterFont(URL url, String fileName, long lastModified, long fileSize, String targetFontFamily) throws IOException {
        String[] names = FontData.getFontName(url);
        int style = 0;

        //System.out.println(Arrays.toString(names));

        String family = names[FontData.NAME_FONT_FAMILY];
        String subfamily = names[FontData.NAME_FONT_SUBFAMILY];

        if(subfamily != null) {
            subfamily = subfamily.toLowerCase(Locale.ENGLISH);
            if(subfamily.contains("italic")) {
                style |= STYLE_ITALIC;
            }
            if(subfamily.contains("bold")) {
                style |= STYLE_BOLD;
            }
            if(subfamily.contains("oblique")) {
                style |= STYLE_ITALIC | REGISTER_WEAK;
            }
        }

        boolean result = registerFont((targetFontFamily != null) ? targetFontFamily : family, style, url);

        if(fontCache != null && fileName != null) {
            fontCache.put(fileName, String.format("%d,%d,%d,%s", lastModified, fileSize, style, family));
            fontCacheChanged = true;
        }

        return result;
    }
    
    private static final int NOT_IN_CACHE = 0;
    private static final int FAILED       = 1;
    private static final int SUCCEEDED    = 2;
    
    private int registerFromCache(URL url, long lastModified, long fileSize, String cacheEntry, String targetFontFamily) {
        if(cacheEntry == null) {
            return NOT_IN_CACHE;
        }
        
        int idx0 = cacheEntry.indexOf(',');
        if(idx0 < 0) {
            return NOT_IN_CACHE;
        }
        
        long cacheLastModified = Long.parseLong(cacheEntry.substring(0, idx0));
        if(cacheLastModified != lastModified) {
            return NOT_IN_CACHE;
        }
        
        int idx1 = cacheEntry.indexOf(',', idx0+1);
        if(idx1 < 0) {
            return NOT_IN_CACHE;
        }
        
        long cacheFileSize = Long.parseLong(cacheEntry.substring(idx0+1, idx1));
        if(cacheFileSize != fileSize) {
            return NOT_IN_CACHE;
        }
        
        int idx2 = cacheEntry.indexOf(',', idx1+1);
        if(idx2 < 0) {
            // file is in cache but marked as invalid font
            return FAILED;
        }
        
        int cacheStyle = Integer.parseInt(cacheEntry.substring(idx1+1, idx2));
        
        String family = cacheEntry.substring(idx2+1);
        return registerFont(
                (targetFontFamily != null) ? targetFontFamily : family,
                cacheStyle, url) ? SUCCEEDED : FAILED;
    }
    
    private void checkFontCacheChanged() {
        if(fontCacheChanged && fontCacheChangedCB != null) {
            fontCacheChanged = false;
            fontCacheChangedCB.run();
        }
    }
    
    @Override
    public void destroy() {
        for(FontData fd : fontData.values()) {
            for(TTFFontRenderer f : fd.fontRenderers.values()) {
                f.destroy();
            }
            fd.destroy();
        }
        fontData.clear();
    }
    
    Logger getLogger() {
        return Logger.getLogger(TWLFontMapper.class.getName());
    }
    
    static class Entry {
        URL url;
        boolean weak;

        Entry(URL url, boolean weak) {
            this.url = url;
            this.weak = weak;
        }
    }
}
