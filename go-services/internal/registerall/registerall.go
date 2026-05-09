// Package registerall blank-imports every generated FINT resource package
// so that runtime.New can resolve any "component:Type" key. Services that
// want the full model surface area blank-import this package once:
//
//	import _ "github.com/FINTLabs/fint-link-walker/go-services/internal/registerall"
//
// A service that only cares about a subset of components can skip this
// package and blank-import the specific ones instead — runtime.New for an
// unregistered type returns nil.
package registerall

import (
	_ "github.com/FINTLabs/fint-model-go/administrasjon/fullmakt"
	_ "github.com/FINTLabs/fint-model-go/administrasjon/kodeverk"
	_ "github.com/FINTLabs/fint-model-go/administrasjon/kompleksedatatyper"
	_ "github.com/FINTLabs/fint-model-go/administrasjon/organisasjon"
	_ "github.com/FINTLabs/fint-model-go/administrasjon/personal"
	_ "github.com/FINTLabs/fint-model-go/arkiv/kodeverk"
	_ "github.com/FINTLabs/fint-model-go/arkiv/kulturminnevern"
	_ "github.com/FINTLabs/fint-model-go/arkiv/noark"
	_ "github.com/FINTLabs/fint-model-go/arkiv/personal"
	_ "github.com/FINTLabs/fint-model-go/arkiv/samferdsel"
	_ "github.com/FINTLabs/fint-model-go/felles"
	_ "github.com/FINTLabs/fint-model-go/felles/basisklasser"
	_ "github.com/FINTLabs/fint-model-go/felles/kodeverk"
	_ "github.com/FINTLabs/fint-model-go/felles/kodeverk/iso"
	_ "github.com/FINTLabs/fint-model-go/felles/kompleksedatatyper"
	_ "github.com/FINTLabs/fint-model-go/okonomi/faktura"
	_ "github.com/FINTLabs/fint-model-go/okonomi/kodeverk"
	_ "github.com/FINTLabs/fint-model-go/okonomi/regnskap"
	_ "github.com/FINTLabs/fint-model-go/personvern/kodeverk"
	_ "github.com/FINTLabs/fint-model-go/personvern/samtykke"
	_ "github.com/FINTLabs/fint-model-go/ressurs/datautstyr"
	_ "github.com/FINTLabs/fint-model-go/ressurs/eiendel"
	_ "github.com/FINTLabs/fint-model-go/ressurs/kodeverk"
	_ "github.com/FINTLabs/fint-model-go/ressurs/tilgang"
	_ "github.com/FINTLabs/fint-model-go/utdanning/basisklasser"
	_ "github.com/FINTLabs/fint-model-go/utdanning/elev"
	_ "github.com/FINTLabs/fint-model-go/utdanning/kodeverk"
	_ "github.com/FINTLabs/fint-model-go/utdanning/larling"
	_ "github.com/FINTLabs/fint-model-go/utdanning/ot"
	_ "github.com/FINTLabs/fint-model-go/utdanning/timeplan"
	_ "github.com/FINTLabs/fint-model-go/utdanning/utdanningsprogram"
	_ "github.com/FINTLabs/fint-model-go/utdanning/vurdering"
)
