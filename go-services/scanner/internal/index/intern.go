package index

import (
	"strings"
	"sync"

	"github.com/FINTLabs/fint-model-go/manifest"
	"github.com/FINTLabs/fint-model-go/runtime"
)

// internRelation returns a deduplicated copy of name. The pool is built
// once from the metamodel — every relation name that any FINT type
// declares — so a hit returns the canonical metamodel string and a miss
// returns the input as-is (rare; means the API returned a relation not
// in the model).
//
// Why this exists: the JSON decoder allocates a fresh string for every
// "_links" key it sees. Across millions of outbound refs the ~100
// unique relation names get duplicated millions of times.
func internRelation(name string) string {
	pool := relationPool()
	lower := strings.ToLower(name)
	if v, ok := pool[lower]; ok {
		return v
	}
	return name
}

var (
	relationPoolOnce sync.Once
	relationPoolMap  map[string]string
)

func relationPool() map[string]string {
	relationPoolOnce.Do(buildRelationPool)
	return relationPoolMap
}

// buildRelationPool walks the metamodel + runtime registry to enumerate
// every declared relation name. Runs once, lazily — relies on the
// runtime dispatch having been populated by the consumer's blank
// imports (typically via internal/registerall).
func buildRelationPool() {
	m := make(map[string]string, 256)
	for _, comp := range manifest.Components {
		for _, t := range comp.Types {
			r := runtime.New(t.Key)
			if r == nil {
				continue
			}
			for _, rel := range r.Relations() {
				lower := strings.ToLower(rel.Name)
				m[lower] = lower
			}
		}
	}
	relationPoolMap = m
}
