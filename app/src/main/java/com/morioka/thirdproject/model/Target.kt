package com.morioka.thirdproject.model

class Target {
    var status: Int = 0
    var name: String = ""
    var targetNumber: Int = 0

    constructor(a: Int, b: String, c: Int){
        status = a
        name = b
        targetNumber = c
    }
}