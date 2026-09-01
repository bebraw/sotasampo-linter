# repair: https://w3id.org/warsampo-linter#RepairDctBibliographiccitation
# rule: https://w3id.org/warsampo-linter#KnownVocabularyTermShape
DELETE { ?s <http://purl.org/dc/terms/bibliographiccitation> ?o }
            INSERT { ?s <http://purl.org/dc/terms/bibliographicCitation> ?o }
            WHERE  { ?s <http://purl.org/dc/terms/bibliographiccitation> ?o } ;
            DELETE { ?s a <http://purl.org/dc/terms/bibliographiccitation> }
            INSERT { ?s a <http://purl.org/dc/terms/bibliographicCitation> }
            WHERE  { ?s a <http://purl.org/dc/terms/bibliographiccitation> }
;

# repair: https://w3id.org/warsampo-linter#RepairOwlSame
# rule: https://w3id.org/warsampo-linter#KnownVocabularyTermShape
DELETE { ?s <http://www.w3.org/2002/07/owl#same> ?o }
            INSERT { ?s <http://www.w3.org/2002/07/owl#sameAs> ?o }
            WHERE  { ?s <http://www.w3.org/2002/07/owl#same> ?o } ;
            DELETE { ?s a <http://www.w3.org/2002/07/owl#same> }
            INSERT { ?s a <http://www.w3.org/2002/07/owl#sameAs> }
            WHERE  { ?s a <http://www.w3.org/2002/07/owl#same> }
;

# repair: https://w3id.org/warsampo-linter#RepairRdfsProperty
# rule: https://w3id.org/warsampo-linter#KnownVocabularyTermShape
DELETE { ?s <http://www.w3.org/2000/01/rdf-schema#Property> ?o }
            INSERT { ?s <http://www.w3.org/1999/02/22-rdf-syntax-ns#Property> ?o }
            WHERE  { ?s <http://www.w3.org/2000/01/rdf-schema#Property> ?o } ;
            DELETE { ?s a <http://www.w3.org/2000/01/rdf-schema#Property> }
            INSERT { ?s a <http://www.w3.org/1999/02/22-rdf-syntax-ns#Property> }
            WHERE  { ?s a <http://www.w3.org/2000/01/rdf-schema#Property> }
;

# repair: https://w3id.org/warsampo-linter#RepairRdfsSubClassof
# rule: https://w3id.org/warsampo-linter#KnownVocabularyTermShape
DELETE { ?s <http://www.w3.org/2000/01/rdf-schema#subClassof> ?o }
            INSERT { ?s <http://www.w3.org/2000/01/rdf-schema#subClassOf> ?o }
            WHERE  { ?s <http://www.w3.org/2000/01/rdf-schema#subClassof> ?o } ;
            DELETE { ?s a <http://www.w3.org/2000/01/rdf-schema#subClassof> }
            INSERT { ?s a <http://www.w3.org/2000/01/rdf-schema#subClassOf> }
            WHERE  { ?s a <http://www.w3.org/2000/01/rdf-schema#subClassof> }
;

# repair: https://w3id.org/warsampo-linter#RepairSkosPreflabel
# rule: https://w3id.org/warsampo-linter#KnownVocabularyTermShape
DELETE { ?s <http://www.w3.org/2004/02/skos/core#preflabel> ?o }
            INSERT { ?s <http://www.w3.org/2004/02/skos/core#prefLabel> ?o }
            WHERE  { ?s <http://www.w3.org/2004/02/skos/core#preflabel> ?o } ;
            DELETE { ?s a <http://www.w3.org/2004/02/skos/core#preflabel> }
            INSERT { ?s a <http://www.w3.org/2004/02/skos/core#prefLabel> }
            WHERE  { ?s a <http://www.w3.org/2004/02/skos/core#preflabel> }
