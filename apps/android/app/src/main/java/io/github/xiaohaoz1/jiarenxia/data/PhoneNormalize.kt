package io.github.xiaohaoz1.jiarenxia.data

/** 号码去重比较键：忽略空格、短横线、括号（DEVELOPMENT_PLAN 2.8 二）。 */
internal fun normalizePhone(number: String): String =
    number.filterNot { it == ' ' || it == '-' || it == '(' || it == ')' }
