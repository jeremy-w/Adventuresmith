/*
 * Copyright (c) 2016 Steve Christensen
 *
 * This file is part of Adventuresmith.
 *
 * Adventuresmith is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Adventuresmith is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Adventuresmith.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.stevesea.adventuresmith.core

import com.fasterxml.jackson.core.*
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.annotation.*
import com.fasterxml.jackson.databind.deser.std.*
import java.util.*

/**
 * this assumes the incoming data is a list of strings. each string needs to be parsed and applied
 * to the rangemap that'll be returned by the deserializer
 */
class RangeMapDeserializer : StdDeserializer<RangeMap>(RangeMap::class.java) {
    override fun deserialize(p: JsonParser?, ctxt: DeserializationContext?): RangeMap {
        if (p == null)
            throw JsonMappingException(p, "null parser received")
        val jsonNode : JsonNode = p.codec.readTree(p)

        // TODO : is there better way to handle these sorts of errors than throwing?
        //    seems like this could be source of subtle crashes (especially as locales are added)
        //    if we just throw all the time.
        if (!jsonNode.isArray) {
            throw JsonMappingException(p, "RangeMap data must be an array of strings")
        }

        val result = RangeMap()

        for (v in jsonNode) {
            if (!v.isTextual)
                continue

            val str = v.asText()
            val words = str.split(",", limit = 2)

            if (words.size != 2) {
                throw JsonMappingException(p, "bad format for RangeMap input: '%s'. Must be <range>,<val>".format(str))
            }

            val rangeStr = words[0]

            result.with(strToIntRange(p, rangeStr), words[1].trim())
        }

        return result
    }

    fun strToIntRange(p: JsonParser,rangeStr: String) : IntRange {
        try {
            val words = rangeStr.split("..", limit = 2)
            if (words.size != 2) {
                // if no '..', must be a single int
                val i = rangeStr.toInt()
                return IntRange(i,i)
            }
            val start = words[0].toInt()
            val end = words[1].toInt()
            return IntRange(start,end)
        } catch (e: NumberFormatException) {
            throw JsonMappingException(p, "bad format for range : '%s'. Must be <Start>..<End> or <Int>. %s".format(rangeStr, e.message))
        }
    }
}

/**
 * a RangeMap is a map meant to hold entries like
 *    1..2 optionA
 *    3..4 optionB
 *    5..9 optionC
 *    10 optionD
 *
 * This implementation assumes that
 *  - there are no holes in the range (does not enforce this)
 *  - there are no overlaps in the range (enforces this)
 */
@JsonDeserialize(using = RangeMapDeserializer::class)
class RangeMap(
        val delegate: TreeMap<Int, String> = TreeMap<Int, String>()
) : Map<Int, String> by delegate {

    var maxKey : Int = -1
    val ranges: MutableSet<IntRange> = mutableSetOf()

    init {
        for (i in delegate.keys) {
            ranges.add(IntRange(i,i))
        }
        if (delegate.keys.size > 0)
            maxKey = delegate.lastKey()
    }

    fun with(newRange: IntRange, value: String) : RangeMap {
        for (range in ranges) {
            if (!range.intersect(newRange).isEmpty()) {
                throw IllegalArgumentException("Invalid range -- already included")
            }
        }
        ranges.add(newRange)

        delegate.put(newRange.start, value)
        maxKey = Math.max(newRange.endInclusive, maxKey)

        return this
    }

    fun with(k: Int, value: String) : RangeMap {
        return with(k..k, value)
    }

    fun select(k: Int) : String {
        return delegate.floorEntry(k).value
    }

    fun select(dice: Dice) : String {
        return select(dice.roll())
    }
}
