/*
 * ordol
 *
 * Copyright (C) 2018 Michael Lux, Fraunhofer AISEC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
#include "analyzer_SSDeep.h"
#include "fuzzy.h"

JNIEXPORT jint JNICALL Java_analyzer_SSDeep_fuzzyCompare(JNIEnv *env, jclass c, jstring sig1, jstring sig2) {
	const char *cSig1 = (*env)->GetStringUTFChars(env, sig1, NULL);
	const char *cSig2 = (*env)->GetStringUTFChars(env, sig2, NULL);
	int comp = fuzzy_compare(cSig1, cSig2);
	(*env)->ReleaseStringUTFChars(env, sig1, cSig1);
	(*env)->ReleaseStringUTFChars(env, sig2, cSig2);
	if (comp < 0) {
		jclass exClass;
		exClass = (*env)->FindClass(env, "java/lang/RuntimeException");
		(*env)->ThrowNew(env, exClass, "Hash comparison failed!");
	}
	return comp;
}

JNIEXPORT jstring JNICALL Java_analyzer_SSDeep_hashString(JNIEnv *env, jclass c, jstring s) {
	const char *cString = (*env)->GetStringUTFChars(env, s, NULL);
	char hash[FUZZY_MAX_RESULT];
	int state = fuzzy_hash_buf(cString, strlen(cString), hash);
	(*env)->ReleaseStringUTFChars(env, s, cString);
	if (state != 0) {
		jclass exClass;
		exClass = (*env)->FindClass(env, "java/lang/RuntimeException");
		(*env)->ThrowNew(env, exClass, "Hashing failed!");
	}
	return (*env)->NewStringUTF(env, hash);
}