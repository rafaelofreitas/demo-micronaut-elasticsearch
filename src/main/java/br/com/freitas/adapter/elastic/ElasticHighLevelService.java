package br.com.freitas.adapter.elastic;

import br.com.freitas.adapter.elastic.mapper.ElasticMapper;
import br.com.freitas.config.RestClientFactory;
import br.com.freitas.core.application.port.elastic.ElasticHighLevelServicePort;
import br.com.freitas.core.domain.Product;
import br.com.freitas.core.exception.InternalServerErrorException;
import br.com.freitas.core.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.DocWriteResponse.Result;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.core.GetSourceRequest;
import org.elasticsearch.common.xcontent.XContentType;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;

@Slf4j
@Singleton
public class ElasticHighLevelService implements ElasticHighLevelServicePort {

    public static final String INDEX = "products";

    private final RestHighLevelClient client = new RestClientFactory().builderHighLevel();

    @Inject
    private ElasticMapper mapper;

    @Override
    public Product post(String id, Product product) {
        var request = new IndexRequest(INDEX).id(id).source(product.toJson(), XContentType.JSON);

        try {
            var response = this.client.index(request, RequestOptions.DEFAULT);

            if (!Result.CREATED.equals(response.getResult())) {
                log.error("Could not add document to index: {}", INDEX);
                throw new InternalServerErrorException("Error adding document to index: " + INDEX);
            }

            return this.get(id);
        } catch (IOException e) {
            log.error("Error adding document to index: {}", INDEX, e);
            throw new InternalServerErrorException("Error adding document!", e);
        }
    }

    @Override
    public Product get(String id) {
        var request = new GetSourceRequest(INDEX, id);

        try {
            var response = this.client.getSource(request, RequestOptions.DEFAULT).getSource();
            return this.mapper.toDomain(response);
        } catch (ElasticsearchException e) {
            log.error("Document not found in index: {} of id: {}", INDEX, id, e);
            throw new NotFoundException(e);
        } catch (IOException e) {
            log.error("Error fetching document from index: {}", INDEX, e);
            throw new InternalServerErrorException("Error fetching document!", e);
        }
    }

    @Override
    public Product put(String id, Product product) {
        var request = new UpdateRequest(INDEX, id).doc(product.toJson(), XContentType.JSON);

        try {
            var response = this.client.update(request, RequestOptions.DEFAULT);
            var status = response.getResult();

            if (!Result.UPDATED.equals(status) && !Result.NOOP.equals(status)) {
                log.error("Unable to update document in index: {}", INDEX);
                throw new InternalServerErrorException("Error updating document in index: " + INDEX);
            }

            return this.get(id);
        } catch (IOException e) {
            log.error("Error updating document in index: {}", INDEX, e);
            throw new InternalServerErrorException("Error updating document!", e);
        } catch (ElasticsearchStatusException e) {
            log.error("Error updating, document not found in index: {} of id: {}", INDEX, id, e);
            throw new NotFoundException(e);
        }
    }

    @Override
    public void delete(String id) {
        var request = new DeleteRequest(INDEX, id);

        try {
            var response = this.client.delete(request, RequestOptions.DEFAULT);

            if (!Result.DELETED.equals(response.getResult())) {
                log.error("Document not found in index: {} of id: {}", INDEX, id);
                throw new NotFoundException("Document not found!");
            }
        } catch (IOException e) {
            log.error("Error deleting document in index: {}", INDEX, e);
            throw new InternalServerErrorException("Error deleting document!", e);
        }
    }

    @Override
    public boolean exist(String id) {
        var request = new GetSourceRequest(INDEX, id);

        try {
            return this.client.existsSource(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            log.error("Error verifying the existence of the document index: {}", INDEX, e);
            return false;
        }
    }
}