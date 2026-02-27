package com.atex.onecms.app.dam.lifecycle.charcount;

import com.atex.onecms.app.dam.standard.aspects.OneArticleBean;
import com.atex.onecms.content.Content;
import com.atex.onecms.content.ContentWrite;
import com.atex.onecms.content.ContentWriteBuilder;
import com.atex.onecms.content.callback.CallbackException;
import com.atex.onecms.content.lifecycle.LifecycleContextPreStore;
import com.atex.onecms.content.lifecycle.LifecyclePreStore;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pre-store hook that counts characters in article body fields
 * and sets the character count on the bean.
 */
public class OneCharCountPreStoreHook implements LifecyclePreStore<Object, Object> {

    private static final Logger LOG = Logger.getLogger(OneCharCountPreStoreHook.class.getName());

    @Override
    public ContentWrite<Object> preStore(ContentWrite<Object> input, Content<Object> existing,
                                          LifecycleContextPreStore<Object> context)
            throws CallbackException {
        Object data = input.getContentData();
        if (!(data instanceof OneArticleBean bean)) {
            return input;
        }

        try {
            int charCount = countChars(bean.getBody());
            bean.setChars(charCount);
            return ContentWriteBuilder.from(input).mainAspectData(bean).build();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error counting characters", e);
            return input;
        }
    }

    static int countChars(String html) {
        if (html == null || html.isEmpty()) return 0;
        String text = stripHtml(html).trim();
        return text.length();
    }

    static String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]*>", "")
                   .replaceAll("&nbsp;", " ")
                   .replaceAll("&\\w+;", " ");
    }
}
