package br.com.challenge.bands.domain.port;

import br.com.challenge.bands.domain.model.Band;
import java.util.List;

public interface BandCatalogPort {

    List<Band> findAll();
}
