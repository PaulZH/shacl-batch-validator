# Shacl Batch Validator

Using this tool you can easily run shacl validations and get reports.

Input shacl and data files can be any type of RDF; output reports are turtle files.

## Using the tool 

From the commandline output

```
usage: java -jar shacl-batch-validator.jar
                [--shacl folder, file or ant-style file pattern] 
                [--validate folder, file or ant-style file pattern ("./conceptscheme/**/*.ttl")] 
optional:       [--destination folder] 
                [--html true (default false)] 
                [--columns comma separated list of html report columns 
                           possible values are: detail, focusNode, resultMessage, resultPath, 
                                                resultSeverity, sourceConstraint, sourceShape, 
                                                sourceConstraintComponent and value
                           default value: resultSeverity,sourceShape,resultPath,resultMessage,focusNode,value
                [--sorting comma separated list of html report sort columns
                           possible values are: detail, focusNode, resultMessage, resultPath, 
                                                resultSeverity, sourceConstraint, sourceShape, 
                                                sourceConstraintComponent and value 
                           default value: sourceShape,resultPath,sourceConstraintComponent,resultMessage 
                [--severity outputs only reports of this severity or higher, possible values Info, Warning or Violation] 
```

Example                     

```
java -jar shacl-batch-validator.jar 
   --shacl ./shacl-examples/*.ttl 
   --validate ./skos/*.ttl
```

will start and show

```
Running with settings: 

		 Shacl           : ./shacl-examples/*.ttl
		 Validate        : ./skos/*.ttl
		 Destination     : .
		 Html            : false
		 Severity        : not set, outputting conforming and non-conforming shacl reports		 
```
                      
## Building

A build will create a `shacl-batch-validator.jar` file. 

### On Windows
                      
```
gradlew.bat build
```

### On Unix

```
./gradlew build
```
