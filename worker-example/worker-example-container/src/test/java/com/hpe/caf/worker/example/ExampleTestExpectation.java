/*
 * Copyright 2015-2017 Hewlett Packard Enterprise Development LP.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hpe.caf.worker.example;

import com.hpe.caf.worker.testing.ContentFileTestExpectation;

/**
 * ExampleTestExpectation forms a component of the test item, and contains the expected ExampleWorkerResult, used to compare
 * with the actual worker result.
 */
public class ExampleTestExpectation  extends ContentFileTestExpectation {

    /**
     * ExampleWorkerResult read in from the yaml test case, used to validate the result of the worker is as expected.
     */
    private ExampleWorkerResult result;

    public ExampleTestExpectation() {
    }

    public ExampleWorkerResult getResult() {
        return result;
    }

    public void setResult(ExampleWorkerResult result) {
        this.result = result;
    }
}
