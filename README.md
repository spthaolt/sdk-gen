# SDK/DOC generator

## Parameters:

* -s or --save - select the file to save entire raml to
* -g or --generator - select the generator
* -r or --resources - select the correct ssp files
* -i or --include - files to include with the generated ones
* -o or --output - where to save the generated files
* last parameter is the path to main raml file

## Example run parameters:
```
--save whole_api.raml -g doc -o generated/docs  -r resources/doc ../api.raml/api.raml
```
