// Package validate walks an Index and emits report.Rows for the two
// problem types the link walker cares about today: missing-resource and
// missing-back-link-adapter. POC scope — no auto-relation rules, no PII
// masking.
package validate

import (
	"github.com/FINTLabs/fint-model-go/runtime"

	"github.com/FINTLabs/fint-link-walker/go-services/pkg/report"
	"github.com/FINTLabs/fint-link-walker/go-services/scanner/internal/index"
)

// Validator runs link-integrity checks against an Index. relationsCache
// memoizes runtime.New(typeKey).Relations() so we only allocate one
// empty struct per unique type during validation.
type Validator struct {
	OrgID          string
	relationsCache map[string][]runtime.Relation
}

func New(orgID string) *Validator {
	return &Validator{
		OrgID:          orgID,
		relationsCache: make(map[string][]runtime.Relation),
	}
}

// Run walks every record's outbound refs and emits one report.Row per
// problem detected.
func (v *Validator) Run(idx *index.Index) []report.Row {
	var rows []report.Row
	for _, rec := range idx.Records {
		sourceSet := canonicalSet(rec.CanonicalKeys)
		for _, ref := range rec.Outbound {
			target, ok := idx.RecordAt(ref.Target)
			if !ok {
				rows = append(rows, report.Row{
					OrgID:        v.OrgID,
					Component:    rec.Component,
					Resource:     rec.ResourceName,
					ProblemType:  report.ProblemMissingResource,
					SourceSelf:   rec.DisplaySelf(),
					TargetHref:   ref.Target,
					RelationName: ref.RelationName,
				})
				continue
			}
			rel := v.findRelation(rec.TypeKey, ref.RelationName)
			if rel == nil || rel.Bidirectional == nil {
				continue // one-way relation: nothing to back-check
			}
			if !target.HasOutboundTo(rel.Bidirectional.InverseName, sourceSet) {
				rows = append(rows, report.Row{
					OrgID:               v.OrgID,
					Component:           rec.Component,
					Resource:            rec.ResourceName,
					ProblemType:         report.ProblemMissingBackLinkAdapter,
					SourceSelf:          rec.DisplaySelf(),
					TargetHref:          target.DisplaySelf(),
					RelationName:        ref.RelationName,
					ExpectedInverseName: rel.Bidirectional.InverseName,
				})
			}
		}
	}
	return rows
}

func (v *Validator) findRelation(typeKey, relationName string) *runtime.Relation {
	rels, ok := v.relationsCache[typeKey]
	if !ok {
		r := runtime.New(typeKey)
		if r == nil {
			v.relationsCache[typeKey] = nil
			return nil
		}
		rels = r.Relations()
		v.relationsCache[typeKey] = rels
	}
	for i := range rels {
		if rels[i].Name == relationName {
			return &rels[i]
		}
	}
	return nil
}

func canonicalSet(keys []string) map[string]struct{} {
	out := make(map[string]struct{}, len(keys))
	for _, k := range keys {
		out[k] = struct{}{}
	}
	return out
}
