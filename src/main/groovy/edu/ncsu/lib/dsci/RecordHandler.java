package edu.ncsu.lib.dsci;

import java.util.Map;

public interface RecordHandler {

    void accept(Map<String, ?> record);
}
