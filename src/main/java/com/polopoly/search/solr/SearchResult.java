package com.polopoly.search.solr;

import java.util.Iterator;

public interface SearchResult extends Iterable<SearchResultPage> {

    int getApproximateNumberOfPages();

    @Override
    Iterator<SearchResultPage> iterator();

    SearchResultPage getPage(int page);
}
