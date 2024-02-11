# toposoid-easy-search-web
This is a WEB API that works as a microservice within the Toposoid project.
Toposoid is a knowledge base construction platform.(see [Toposoid　Root Project](https://github.com/toposoid/toposoid.git))
This microservice provides a simple search API. Specifically, it supports text search and image search.

[![Test And Build](https://github.com/toposoid/toposoid-easy-search-web/actions/workflows/action.yml/badge.svg)](https://github.com/toposoid/toposoid-easy-search-web/actions/workflows/action.yml)

* Text Search
<img width="1273"  src="https://github.com/toposoid/toposoid-easy-search-web/assets/82787843/4fac7590-7aa6-4bc1-a579-82521905eed6">
* Image Search
<img width="1166"  src="https://github.com/toposoid/toposoid-easy-search-web/assets/82787843/3624b455-7fb0-419e-bb4b-fd681b67d325">


## Requirements
* Docker version 20.10.x, or later
* docker-compose version 1.22.x
* The following microservices must be running
  * toposoid/toposoid-sentence-parser-japanese-web
  * toposoid/toposoid-sentence-parser-english-web
  * toposoid/toposoid-common-nlp-japanese-web
  * toposoid/toposoid-common-nlp-english-web
  * toposoid-common-image-recognition-web
  * toposoid/toposoid-contents-admin-web
  * toposoid/data-accessor-weaviate-web
  * semitechnologies/weaviate
  * neo4j


## Memory requirements For Standalone
* Required: at least 16GB of RAM
* Required: at least 40G of HDD(Total required Docker Image size) 
* Please understand that since we are dealing with large models such as LLM, the Dockerfile size is large and the required machine SPEC is high.


## Setup For Standalone
```bssh
docker-compose up
```
* It takes more than 20 minutes to pull the Docker image for the first time.

## Usage
```bash
# Please refer to the following for information on registering data to try searching.
# ref. https://github.com/toposoid/toposoid-knowledge-register-web
#for example
curl -X POST -H "Content-Type: application/json" -d '{
    "premiseList": [],
    "premiseLogicRelation": [],
    "claimList": [
        {
            "sentence": "猫が２匹います。",
            "lang": "ja_JP",
            "extentInfoJson": "{}",
            "isNegativeSentence": false,
            "knowledgeForImages":[{
                "id": "",
                "imageReference": {
                    "reference": {
                        "url": "",
                        "surface": "猫が",
                        "surfaceIndex": 0,
                        "isWholeSentence": true,
                        "originalUrlOrReference": "http://images.cocodataset.org/val2017/000000039769.jpg"},
                    "x": 0,
                    "y": 0,
                    "width": 0,
                    "height": 0
                }
            }]
        }
    ],
    "claimLogicRelation": [
    ]
}' http://localhost:9004/analyzeKnowledgeTree


#Text Search
curl -X POST -H "Content-Type: application/json" -d '{
    "sentence":"猫",
    "lang":"ja_JP",
    "similarityThreshold":0.85
}
' http://localhost:9004/analyzeKnowledgeTree

#Image Search
curl -X POST -H "Content-Type: application/json" -d '{
    "url": "http://images.cocodataset.org/val2017/000000039769.jpg",
    "lang": "ja_JP",
    "similarityThreshold": 0.9,
    "isUploaded": false
}' http://localhost:9004/analyze
```

## Note
* This microservice uses 9014 as the default port.
* If you want to run in a remote environment or a virtual environment, change PRIVATE_IP_ADDRESS in docker-compose.yml according to your environment.

## License
toposoid/toposoid-easy-search-web is Open Source software released under the [Apache 2.0 license](https://www.apache.org/licenses/LICENSE-2.0.html).

## Author
* Makoto Kubodera([Linked Ideal LLC.](https://linked-ideal.com/))

Thank you!