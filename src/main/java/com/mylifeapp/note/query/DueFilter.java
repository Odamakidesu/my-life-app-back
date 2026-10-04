package com.mylifeapp.note.query;

/** 締切による絞り込み。どちらも未完了のメモだけが対象。 */
public enum DueFilter {
    /** 締切を過ぎた */
    OVERDUE,
    /** 今から24時間以内に締切が来る */
    SOON
}
