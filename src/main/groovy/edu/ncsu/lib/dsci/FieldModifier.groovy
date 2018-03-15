package edu.ncsu.lib.dsci

import groovy.json.JsonOutput

class FieldModifier {


    FieldModifier(Set fieldNames, Writer output) {
        def commands = fieldNames.findAll { it != 'id' }.collect { f ->
            [ name: f, type: 'text_general', stored: true, multiValued: true ]
        }
        def result = [ 'add-field' : commands ]
        output.println new JsonOutput().toJson(result)
    }
}
