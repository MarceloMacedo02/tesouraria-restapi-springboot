package br.com.areadigital.aplicativo.services.impl.socio;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import br.com.areadigital.aplicativo.dtos.CampoPesquisaDto;
import br.com.areadigital.aplicativo.dtos.ConsultaPaginadaDto;
import br.com.areadigital.aplicativo.dtos.socio.FichaSocioDto;
import br.com.areadigital.aplicativo.dtos.socio.SocioDto;
import br.com.areadigital.aplicativo.entities.enums.StatusMovimento;
import br.com.areadigital.aplicativo.entities.enums.TipoEntradaFicha;
import br.com.areadigital.aplicativo.entities.socio.FichaConta;
import br.com.areadigital.aplicativo.entities.socio.FichaSocio;
import br.com.areadigital.aplicativo.entities.socio.Socio;
import br.com.areadigital.aplicativo.mappers.impl.socio.SocioMapper;
import br.com.areadigital.aplicativo.repositories.FichaContaRepository;
import br.com.areadigital.aplicativo.repositories.FichaSocioRepository;
import br.com.areadigital.aplicativo.repositories.SocioRepository;
import br.com.areadigital.aplicativo.services.AbstractService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@RequiredArgsConstructor
@Service
public class SocioService extends AbstractService<String, Socio, SocioDto> {

    private final SocioRepository repository;
    private final FichaContaRepository fichaContaRepository;
    private final FichaSocioRepository fichaSocioRepository;
    private final SocioMapper mapper;

    @Override
    public Page<SocioDto> findAllWithPaginationAndSorting(ConsultaPaginadaDto consultaPaginadaDTO) {
        CampoPesquisaDto[] campoPesquisa = consultaPaginadaDTO.getCampoPesquisa();
        int page = consultaPaginadaDTO.getPage();
        int tamanho = consultaPaginadaDTO.getTamanho();
        String ordem = consultaPaginadaDTO.getOrdem();
        String ordenarPor = consultaPaginadaDTO.getOrdenarPor();

        PageRequest pageRequest = PageRequest.of(page, tamanho, Sort.Direction.fromString(ordem), ordenarPor);
        return repository.findAllByNomeContainingIgnoreCase(campoPesquisa[0].getValorPesquisado(), pageRequest)
                .map(mapper::toDTO);
    }

    /**
     * Process fichas socio for a specific socio and fichaConta.
     *
     * @param  socio_id       the ID of the socio
     * @param  fichaConta_id  the ID of the fichaConta
     * @return                a list of FichaSocioDto objects
     */
    public List<FichaSocioDto> processarFichasSocio(String socio_id, String fichaConta_id) {

        FichaConta fichaConta = fichaContaRepository.findById(fichaConta_id).get();
        Socio socio = repository.findById(socio_id).get();
        return processarFichasSocio(socio.getSocioTesouraria().getFichaSocios(), fichaConta, new Date()).stream()
                .map(FichaSocioDto::new).toList();
    }

    public List<FichaSocio> processarFichasSocio(List<FichaSocio> fichasSocio, FichaConta fichaConta,
            Date dataInicial) {

        fichasSocio.removeIf(fichaSocio -> (StatusMovimento.ABERTO.getName().equals(fichaSocio.getStatusPagamento())
                && fichaSocio.getFichaConta().getDescricao().equals(TipoEntradaFicha.MENSALIDADE.getName())));

        fichaSocioRepository.saveAll(fichasSocio);
        // Encontrar o último mês em que foi registrado um pagamento quitado
        Calendar ultimoMesQuitado = Calendar.getInstance();
        ultimoMesQuitado.set(Calendar.MONTH, -1); // Inicializa com o mês anterior ao mês atual
        for (FichaSocio ficha : fichasSocio) {
            if (StatusMovimento.QUITADO.getName().equals(ficha.getStatusPagamento())
                    && ficha.getDataVencimento().after(ultimoMesQuitado.getTime())) {

                ultimoMesQuitado.setTime(ficha.getDataVencimento());
            }
        }
        Calendar dataIniciCalendar = Calendar.getInstance();
        dataIniciCalendar.setTime(dataInicial);
        if (ultimoMesQuitado.getTime().compareTo(dataIniciCalendar.getTime()) < 0) {
            ultimoMesQuitado = dataIniciCalendar;
        }
        // Avançar para o próximo mês após o último mês quitado
        ultimoMesQuitado.add(Calendar.MONTH, 1);

        // Definir o dia de vencimento como dia 10
        ultimoMesQuitado.set(Calendar.DAY_OF_MONTH, 10);

        // Criar novas instâncias com statusPagamento igual a "Quitado" e dataVencimento
        // a partir do próximo mês após o último mês quitado até dezembro do corrente
        // ano no dia 10
        Calendar calendar = ultimoMesQuitado;
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);

        while (calendar.get(Calendar.YEAR) == currentYear && calendar.get(Calendar.MONTH) <= 11) {
            FichaSocio novaFichaSocio = new FichaSocio();
            novaFichaSocio.setStatusPagamento(StatusMovimento.ABERTO.name());
            novaFichaSocio.setFichaConta(new FichaConta());
            novaFichaSocio.getFichaConta().setId(fichaConta.getId());
            novaFichaSocio.setDataVencimento(calendar.getTime());

            fichaSocioRepository.save(novaFichaSocio);
            fichasSocio.add(novaFichaSocio);

            calendar.add(Calendar.MONTH, 1); // Avançar para o próximo mês
        }
        return fichasSocio;
    }

}
