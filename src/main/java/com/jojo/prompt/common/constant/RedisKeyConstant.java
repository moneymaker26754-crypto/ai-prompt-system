package com.jojo.prompt.common.constant;


public interface RedisKeyConstant {

    //提示词缓存key
    String PROMPT_DEFAULT = "prompt:detail:";

    //提示词空值缓存key
    String PROMPT_NULL =  "prompt:null:";

    //分类列表缓存key
    String CATEGORY_LIST = "category:list:all:";

    //热门提示词排行榜key（按点赞量）
    String PROMPT_HOT_LIKE = "prompt:hot:like:";

    //热门提示词排行榜key（按浏览量）
    String PROMPT_HOT_VIEW = "prompt:hot:view:";

    //热门提示词排行榜key（按收藏量）
    String PROMPT_HOT_FAVORITE =  "prompt:hot:favorite:";

    //热门提示词排行榜key（按复制量）
    String  PROMPT_HOT_COPY = "prompt:hot:copy:";

    //提示词点赞量缓存key
    String PROMPT_LIKE_COUNT =  "prompt:like:count:";

    //提示词收藏缓量存key
    String PROMPT_FAVORITE_COUNT = "prompt:favorite:count:";

    //提示词浏览量缓存key
    String PROMPT_VIEW_COUNT = "prompt:view:count:";

    //提示词复制量缓存key
    String PROMPT_COPY_COUNT = "prompt:copy:count:";

    //热门关键字搜索
    String SEARCH_HOT_KEYWORDS = "search:hot:keywords:";

    //用户缓存相关key
    String USER_LIKE_SET = "user:like:set:";
    String USER_FAVORITE_SET = "user:favorite:set:";

    //限流相关
    String RATE_LIMIT_SEARCH = "rate:limit:search:";
    long SEARCH_LIMIT_PRE_MINUTE = 20L;
    //复制去重
    String COPY_DEDUP = "prompt:copy:dedup:";
    long COPY_DEDUP_WINDOW_SECONDS = 60L;

    //缓存过期时间基础值（秒），使用时需通过 jitter() 动态加随机偏移防止缓存雪崩
    long CACHE_EXPIRE_30MIN = 30 * 60;
    long CACHE_EXPIRE_1HOUR = 60 * 60;
    long CACHE_EXPIRE_1DAY = 24 * 60 * 60;
    long CACHE_EXPIRE_7DAY = 7 * 24 * 60 * 60;
    long RATE_LIMIT_EXPIRE_1MIN = 60;


    //应对计数脏读问题
    String PROMPT_COUNT_DIRTY_SET = "prompt:count:dirty:";
    //redis锁常量
    String PROMPT_COUNT_SYNC_LOCK = "prompt:count:sync:lock:";
}
