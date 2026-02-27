package com.atex.onecms.app.dam.lifecycle.wordcount;

import com.atex.onecms.app.dam.standard.aspects.OneArticleBean;
import com.atex.onecms.app.dam.standard.aspects.OneContentBean;
import com.atex.onecms.content.Content;
import com.atex.onecms.content.ContentWrite;
import com.atex.onecms.content.ContentWriteBuilder;
import com.atex.onecms.content.callback.CallbackException;
import com.atex.onecms.content.lifecycle.LifecycleContextPreStore;
import com.atex.onecms.content.lifecycle.LifecyclePreStore;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pre-store hook that counts words in article body fields and sets
 * the word count on the bean.
 */
public class OneWordCountPreStoreHook implements LifecyclePreStore<Object, Object> {

    private static final Logger LOG = Logger.getLogger(OneWordCountPreStoreHook.class.getName());

    @Override
    public ContentWrite<Object> preStore(ContentWrite<Object> input, Content<Object> existing,
                                          LifecycleContextPreStore<Object> context)
            throws CallbackException {
        Object data = input.getContentData();
        if (!(data instanceof OneArticleBean bean)) {
            return input;
        }

        try {
            int wordCount = countWords(bean.getBody());
            bean.setWords(wordCount);
            return ContentWriteBuilder.from(input).mainAspectData(bean).build();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error counting words", e);
            return input;
        }
    }

    static int countWords(String html) {
        if (html == null || html.isEmpty()) return 0;
        String text = stripHtml(html).trim();
        if (text.isEmpty()) return 0;
        return text.split("\\s+").length;
    }

    static String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]*>", " ")
                   .replaceAll("&nbsp;", " ")
                   .replaceAll("&\\w+;", " ");
    }
}
