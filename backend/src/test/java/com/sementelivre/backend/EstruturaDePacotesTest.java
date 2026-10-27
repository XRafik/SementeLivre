package com.sementelivre.backend;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import com.sementelivre.backend.repository.BaseRepository;

import jakarta.persistence.Entity;

/**
 * Convenção de pacotes do readme (seção 10): cada tipo de classe fica na sua
 * camada e todo repositório estende BaseRepository. Falha apontando a classe
 * fora do lugar.
 */
class EstruturaDePacotesTest {

    private static final String BASE = "com.sementelivre.backend";

    /** Varre o código do backend; com {@code incluirInterfaces} considera também interfaces (repositórios). */
    private static List<Class<?>> classes(org.springframework.core.type.filter.TypeFilter filtro, boolean incluirInterfaces)
            throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition definicao) {
                return incluirInterfaces || super.isCandidateComponent(definicao);
            }
        };
        scanner.addIncludeFilter(filtro);
        List<Class<?>> encontradas = new ArrayList<>();
        for (BeanDefinition definicao : scanner.findCandidateComponents(BASE)) {
            encontradas.add(Class.forName(definicao.getBeanClassName()));
        }
        return encontradas;
    }

    private static void assertNoPacote(Class<? extends Annotation> anotacao, String... pacotes) throws ClassNotFoundException {
        List<String> permitidos = java.util.Arrays.stream(pacotes).map(p -> BASE + "." + p).toList();
        List<Class<?>> classes = classes(new AnnotationTypeFilter(anotacao), false);
        List<String> foraDoLugar = classes.stream()
                .filter(c -> !permitidos.contains(c.getPackageName()))
                .map(Class::getName)
                .toList();
        assertTrue(!classes.isEmpty(), "nenhuma classe com @" + anotacao.getSimpleName());
        assertTrue(foraDoLugar.isEmpty(), "@" + anotacao.getSimpleName() + " fora de " + permitidos + ": " + foraDoLugar);
    }

    @Test
    void repositoriosFicamEmRepositoryEEstendemBaseRepository() throws ClassNotFoundException {
        List<Class<?>> repositorios = classes(new AssignableTypeFilter(Repository.class), true).stream()
                .filter(Class::isInterface)
                .filter(c -> c != BaseRepository.class)
                .toList();

        List<String> foraDoPacote = repositorios.stream()
                .filter(c -> !c.getPackageName().equals(BASE + ".repository"))
                .map(Class::getName)
                .toList();
        List<String> semBase = repositorios.stream()
                .filter(c -> !BaseRepository.class.isAssignableFrom(c))
                .map(Class::getSimpleName)
                .toList();

        assertTrue(repositorios.size() > 10, "poucos repositórios encontrados: " + repositorios);
        assertTrue(foraDoPacote.isEmpty(), "repositórios fora de repository: " + foraDoPacote);
        assertTrue(semBase.isEmpty(), "repositórios que não estendem BaseRepository: " + semBase);
    }

    @Test
    void controllersFicamEmController() throws ClassNotFoundException {
        assertNoPacote(RestController.class, "controller");
    }

    @Test
    void servicesFicamEmService() throws ClassNotFoundException {
        // JwtService e UserDetailsServiceImpl são da camada de autenticação
        assertNoPacote(Service.class, "service", "security");
    }

    @Test
    void entidadesFicamEmEntity() throws ClassNotFoundException {
        assertNoPacote(Entity.class, "entity");
    }
}
